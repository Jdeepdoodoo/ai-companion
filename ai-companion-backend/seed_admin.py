import asyncio
import os
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select

from db.database import async_session_maker
from db.models import User, RoleEnum
from api.auth import get_password_hash

async def seed_admin():
    async with async_session_maker() as session:
        result = await session.execute(select(User).where(User.username == "JD"))
        admin = result.scalars().first()
        if not admin:
            print("Creating initial admin user 'JD'...")
            # Use environment variable or fallback to a default
            admin_password = os.environ.get("ADMIN_PASSWORD", "changeme123")
            admin = User(
                username="JD",
                password_hash=get_password_hash(admin_password),
                role=RoleEnum.admin
            )
            session.add(admin)
            await session.commit()
            print("Admin user created successfully.")
        else:
            print("Admin user already exists.")

if __name__ == "__main__":
    asyncio.run(seed_admin())
