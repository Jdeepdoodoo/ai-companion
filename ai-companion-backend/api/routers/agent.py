from typing import Literal, Optional
from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from api.dependencies import get_current_user
from db.database import get_db
from db.models import User, AgentRun
from worker.queue import enqueue_task

router = APIRouter(prefix="/api/client", tags=["client"])

class ClientPayload(BaseModel):
    source: Literal["voice", "notification", "text"]
    text: str

class SDUIResponse(BaseModel):
    schema_version: int = 1
    status: str
    message: Optional[str] = None
    components: Optional[list] = None

@router.post("/execute")
async def execute_agent(payload: ClientPayload, current_user: User = Depends(get_current_user)):
    """Enqueues a task from the Android client."""
    run_id = await enqueue_task(current_user.id, payload.text)
    return {"status": "enqueued", "run_id": run_id}

@router.get("/run/{run_id}", response_model=SDUIResponse)
async def get_run_status(run_id: int, current_user: User = Depends(get_current_user), session: AsyncSession = Depends(get_db)):
    """Polls the status of an agent run and returns SDUI if completed."""
    result = await session.execute(
        select(AgentRun).where(AgentRun.id == run_id, AgentRun.user_id == current_user.id)
    )
    run = result.scalars().first()
    
    if not run:
        raise HTTPException(status_code=404, detail="Run not found")
        
    if run.status == "completed":
        # Package output into SDUI
        return SDUIResponse(
            status="completed",
            message=run.output_data.get("response", "Success"),
            components=[
                {"type": "text_card", "content": run.output_data.get("response", "Success")}
            ]
        )
    elif run.status == "failed":
        return SDUIResponse(
            status="failed",
            message="An error occurred while processing the request.",
            components=[
                {"type": "error_banner", "content": "Failed to process request."}
            ]
        )
    else:
        return SDUIResponse(
            status=run.status,
            message="Processing...",
            components=[]
        )
