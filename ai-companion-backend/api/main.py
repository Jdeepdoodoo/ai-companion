import asyncio
from contextlib import asynccontextmanager
from fastapi import FastAPI
from psycopg_pool import AsyncConnectionPool
from langgraph.checkpoint.postgres.aio import AsyncPostgresSaver

from api.routers import auth, agent, console
from core.config import settings
from worker.queue import worker_loop, redis_client

from backup.scheduler import start_scheduler

# Global references for the connection pool and checkpointer
postgres_pool = None
checkpointer = None
worker_task = None
backup_scheduler = None

@asynccontextmanager
async def lifespan(app: FastAPI):
    global postgres_pool, checkpointer, worker_task, backup_scheduler
    
    conn_string = settings.database_url.replace("postgresql+asyncpg://", "postgresql://")
    
    # Initialize connection pool for LangGraph checkpointer
    postgres_pool = AsyncConnectionPool(
        conninfo=conn_string,
        max_size=20,
        kwargs={"autocommit": True},
        open=False
    )
    await postgres_pool.open()
    
    checkpointer = AsyncPostgresSaver(postgres_pool)
    await checkpointer.setup()
    
    # Start the async background worker loop
    worker_task = asyncio.create_task(worker_loop(checkpointer))
    
    # Start the backup scheduler
    backup_scheduler = start_scheduler()
    
    yield
    
    # Clean up
    if backup_scheduler:
        backup_scheduler.shutdown()
        
    if worker_task:
        worker_task.cancel()
        try:
            await worker_task
        except asyncio.CancelledError:
            pass
            
    await redis_client.close()
    await postgres_pool.close()

app = FastAPI(title="Hydra Station API", version="0.1.0", lifespan=lifespan)

app.include_router(auth.router)
app.include_router(agent.router)
app.include_router(console.router)

@app.get("/health")
async def health_check():
    return {"status": "ok", "message": "Hydra Station is running"}

@app.get("/")
async def root():
    return {"message": "Welcome to Hydra Station API"}
