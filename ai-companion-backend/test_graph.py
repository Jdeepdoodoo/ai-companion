import asyncio
from psycopg_pool import AsyncConnectionPool
from langgraph.checkpoint.postgres.aio import AsyncPostgresSaver
from core.config import settings
from agents.graph import get_compiled_graph

async def main():
    conn_string = settings.database_url.replace("postgresql+asyncpg://", "postgresql://")
    print("Conn string:", conn_string)
    
    async with AsyncConnectionPool(
        conninfo=conn_string,
        max_size=5,
        kwargs={"autocommit": True}
    ) as pool:
        saver = AsyncPostgresSaver(pool)
        await saver.setup()
        
        graph = get_compiled_graph(saver)
        print("Graph compiled successfully!")

if __name__ == "__main__":
    asyncio.run(main())
