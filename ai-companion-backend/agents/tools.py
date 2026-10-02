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


import sys
import json
import os
import uuid
from datetime import datetime

# Make sure we can import hydra_brain modules if needed
sys.path.append("/home/ubuntu/code/hydra-brain")

@tool
async def add_stock_trade(ticker: str, action: str, quantity: int, price_cents: int, config: RunnableConfig) -> str:
    """Logs a new stock trade (buy/sell) for the current user in the paper_trader ledger."""
    try:
        # Load existing trades
        trades_file = '/home/ubuntu/code/hydra-brain/data/paper_trades.json'
        if os.path.exists(trades_file):
            with open(trades_file, 'r') as f:
                trades = json.load(f)
        else:
            trades = {}
            
        price_inr = price_cents / 100.0
        today_str = datetime.now().strftime("%Y-%m-%d")
        
        if action.upper() == "BUY":
            trade_id = f"{ticker.upper()}_MANUAL_{uuid.uuid4().hex[:6]}"
            
            # Zerodha charges approx 0.1% for delivery
            charges = price_inr * quantity * 0.001 
            
            trades[trade_id] = {
                "id": trade_id,
                "date_found": today_str,
                "date_entered": today_str,
                "date_closed": "",
                "symbol": ticker.upper(),
                "status": "ACTIVE",
                "strategy_text": "Manual AI Trade",
                "allocated_capital": price_inr * quantity,
                "allocation_pct": 0,
                "invested_capital": price_inr * quantity,
                "qty": quantity,
                "entry_charges": charges,
                "exit_charges": 0,
                "taxes": 0,
                "total_deductions": charges,
                "entry_plan": price_inr,
                "avg_executed": price_inr,
                "current_price": price_inr,
                "exit_price": 0,
                "stop_loss": 0,
                "target": 0,
                "pnl_percent": 0,
                "pnl_absolute": 0,
                "days_held": 0,
                "exit_reason": "",
                "gcsr_found": "N/A",
                "gcsr_entered": "N/A",
                "gcsr_exited": "N/A",
                "notes": "Logged via Hydra AI Companion"
            }
            msg = f"Successfully bought {quantity} {ticker} at ₹{price_inr}."
            
        elif action.upper() == "SELL":
            # Find active trade and close it
            closed_qty = 0
            for tid, t in trades.items():
                if t['symbol'] == ticker.upper() and t['status'] == 'ACTIVE':
                    t['status'] = "CLOSED"
                    t['exit_price'] = price_inr
                    t['date_closed'] = today_str
                    t['exit_reason'] = "MANUAL SELL"
                    
                    gross_pnl = (price_inr - t['avg_executed']) * t['qty']
                    t['pnl_absolute'] = gross_pnl
                    if t['invested_capital'] > 0:
                        t['pnl_percent'] = (gross_pnl / t['invested_capital']) * 100
                        
                    closed_qty += t['qty']
                    
            if closed_qty == 0:
                return f"No active {ticker} trades found to sell."
            msg = f"Successfully sold {closed_qty} {ticker} at ₹{price_inr}."
            
        else:
            return f"Invalid action: {action}"
            
        with open(trades_file, 'w') as f:
            json.dump(trades, f, indent=2)
            
        return msg
        
    except Exception as e:
        return f"Failed to log trade: {str(e)}"

