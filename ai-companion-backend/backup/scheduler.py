import logging
from apscheduler.schedulers.asyncio import AsyncIOScheduler
from sqlalchemy import select

from db.database import async_session_maker
from db.models import User, Expense, StockTrade
from backup.provider import LocalFileBackupProvider

logger = logging.getLogger(__name__)

async def perform_backup():
    """Extracts data from the DB and dumps it using the backup provider."""
    logger.info("Starting scheduled database backup...")
    provider = LocalFileBackupProvider(backup_dir="backup/data")
    
    snapshot = {
        "users": [],
        "expenses": [],
        "stock_trades": []
    }
    
    try:
        async with async_session_maker() as session:
            # Export users
            res_users = await session.execute(select(User))
            for u in res_users.scalars().all():
                snapshot["users"].append({
                    "id": u.id,
                    "username": u.username,
                    "role": u.role.value,
                    "created_at": u.created_at
                })
                
            # Export expenses
            res_expenses = await session.execute(select(Expense))
            for e in res_expenses.scalars().all():
                snapshot["expenses"].append({
                    "id": e.id,
                    "user_id": e.user_id,
                    "amount": e.amount,
                    "category": e.category,
                    "description": e.description,
                    "date": e.date
                })
                
            # Export stock trades
            res_stocks = await session.execute(select(StockTrade))
            for s in res_stocks.scalars().all():
                snapshot["stock_trades"].append({
                    "id": s.id,
                    "user_id": s.user_id,
                    "ticker": s.ticker,
                    "action": s.action,
                    "quantity": s.quantity,
                    "price_cents": s.price_cents,
                    "date": s.date
                })
        
        await provider.export_snapshot(snapshot)
        logger.info("Backup completed successfully.")
    except Exception as e:
        logger.error(f"Backup failed: {e}")

def start_scheduler():
    scheduler = AsyncIOScheduler()
    # Schedule backup every day at 2 AM (just an example, or every 1 hour)
    scheduler.add_job(perform_backup, 'interval', hours=12)
    scheduler.start()
    return scheduler
