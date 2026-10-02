import asyncio
import json
import logging
import traceback
from datetime import datetime

import redis.asyncio as redis
from sqlalchemy import select
from langchain_core.messages import HumanMessage
from langchain_core.runnables import RunnableConfig

from core.config import settings
from db.database import async_session_maker
from db.models import AgentRun, User
from agents.graph import get_compiled_graph

logger = logging.getLogger(__name__)

# Single global Redis client for the queue
redis_client = redis.from_url(settings.REDIS_URL, decode_responses=True)
QUEUE_NAME = "agent_tasks"

async def enqueue_task(user_id: int, user_input: str):
    """Pushes a task onto the Redis queue and creates an AgentRun record."""
    async with async_session_maker() as session:
        # Create pending run
        run = AgentRun(
            user_id=user_id,
            agent_name="router",
            status="pending",
            input_data={"user_input": user_input}
        )
        session.add(run)
        await session.commit()
        await session.refresh(run)
        
        task_payload = json.dumps({
            "run_id": run.id,
            "user_id": user_id,
            "user_input": user_input
        })
        
        await redis_client.lpush(QUEUE_NAME, task_payload)
        return run.id


async def process_task(task_payload: str, checkpointer):
    """Processes a single task using the LangGraph agent."""
    data = json.loads(task_payload)
    run_id = data["run_id"]
    user_id = data["user_id"]
    user_input = data.get("user_input")
    resume_data = data.get("resume_data")
    
    async with async_session_maker() as session:
        # Update run status
        result = await session.execute(select(AgentRun).where(AgentRun.id == run_id))
        run = result.scalars().first()
        if not run:
            logger.error(f"Run {run_id} not found in database.")
            return
            
        run.status = "running"
        await session.commit()
        
    try:
        graph = get_compiled_graph(checkpointer)
        config = {
            "configurable": {
                "thread_id": str(user_id),
                "user_id": user_id
            }
        }
        
        from langgraph.types import Command
        
        if resume_data:
            # We are resuming an interrupted graph
            logger.info(f"Resuming run {run_id} with data {resume_data}")
            response = await graph.ainvoke(Command(resume=resume_data), config=config)
        else:
            response = await graph.ainvoke(
                {"messages": [HumanMessage(content=user_input)]},
                config=config
            )
            
        # Check if the graph is paused/interrupted
        state = await graph.aget_state(config)
        
        async with async_session_maker() as session:
            result = await session.execute(select(AgentRun).where(AgentRun.id == run_id))
            run = result.scalars().first()
            
            if state.next:
                # Graph is interrupted! Find the pending tool calls
                last_msg = state.values["messages"][-1]
                tool_calls = getattr(last_msg, "tool_calls", [])
                
                run.status = "draft"
                run.output_data = {"tool_calls": tool_calls}
            else:
                # Completed
                final_message = state.values["messages"][-1].content
                if isinstance(final_message, list): 
                    final_message = " ".join(m.get("text", "") if isinstance(m, dict) else str(m) for m in final_message)
                
                run.status = "completed"
                run.output_data = {"response": final_message}
                run.completed_at = datetime.utcnow()
                
            await session.commit()
            
    except Exception as e:
        logger.error(f"Task {run_id} failed: {e}")
        async with async_session_maker() as session:
            result = await session.execute(select(AgentRun).where(AgentRun.id == run_id))
            run = result.scalars().first()
            run.status = "failed"
            run.error_message = str(e) + "
" + traceback.format_exc()
            run.completed_at = datetime.utcnow()
            await session.commit()


async def worker_loop(checkpointer):
    """Continuously polls Redis for new jobs and processes them."""
    logger.info("Starting Redis worker loop...")
    while True:
        try:
            # BRPOP blocks until an item is available or timeout (5 seconds)
            result = await redis_client.brpop(QUEUE_NAME, timeout=5)
            if result:
                _, task_payload = result
                # Process task asynchronously so we don't block the loop on long operations?
                # The plan says "without blocking the FastAPI event loop", doing it directly here
                # is fine since `process_task` awaits async IO operations. 
                # To be fully concurrent we could spawn an asyncio Task.
                asyncio.create_task(process_task(task_payload, checkpointer))
        except asyncio.CancelledError:
            logger.info("Worker loop cancelled.")
            break
        except Exception as e:
            logger.error(f"Worker loop error: {e}")
            await asyncio.sleep(1) # Prevent tight loop on Redis connection failure
