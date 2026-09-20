from langchain_core.tools import tool
from langchain_core.runnables import RunnableConfig
from sqlalchemy import select

from db.database import async_session_maker
from db.models import Expense, StockTrade

@tool
async def add_expense(amount_cents: int, category: str, description: str, config: RunnableConfig) -> str:
    """Adds a new expense for the current user. amount_cents must be an integer representing cents."""
    user_id = config.get("configurable", {}).get("user_id")
    if not user_id:
        return "Error: user_id not provided in context."
        
    async with async_session_maker() as session:
        new_expense = Expense(
            user_id=user_id,
            amount=amount_cents,
            category=category,
            description=description
        )
        session.add(new_expense)
        await session.commit()
        return f"Successfully added expense of {amount_cents} cents for '{category}'."

@tool
async def get_recent_expenses(limit: int, config: RunnableConfig) -> str:
    """Retrieves the recent expenses for the current user."""
    user_id = config.get("configurable", {}).get("user_id")
    if not user_id:
        return "Error: user_id not provided in context."
        
    async with async_session_maker() as session:
        result = await session.execute(
            select(Expense).where(Expense.user_id == user_id).order_by(Expense.date.desc()).limit(limit)
        )
        expenses = result.scalars().all()
        if not expenses:
            return "No expenses found."
        
        lines = []
        for e in expenses:
            lines.append(f"Date: {e.date}, Category: {e.category}, Amount: {e.amount} cents, Desc: {e.description}")
        return "\n".join(lines)

@tool
async def add_stock_trade(ticker: str, action: str, quantity: int, price_cents: int, config: RunnableConfig) -> str:
    """Logs a new stock trade (buy/sell) for the current user."""
    user_id = config.get("configurable", {}).get("user_id")
    if not user_id:
        return "Error: user_id not provided in context."
        
    async with async_session_maker() as session:
        new_trade = StockTrade(
            user_id=user_id,
            ticker=ticker,
            action=action,
            quantity=quantity,
            price_cents=price_cents
        )
        session.add(new_trade)
        await session.commit()
        return f"Successfully logged {action} of {quantity} {ticker} at {price_cents} cents."
