import operator
from typing import Annotated, Sequence, TypedDict, Literal

from langchain_core.messages import BaseMessage, HumanMessage
from langgraph.graph import StateGraph, END
from langgraph.checkpoint.postgres.aio import AsyncPostgresSaver
from langgraph.prebuilt import ToolNode

from langchain_google_genai import ChatGoogleGenerativeAI
from pydantic import BaseModel, Field

from agents.tools import add_expense, get_recent_expenses, add_stock_trade

class AgentState(TypedDict):
    messages: Annotated[Sequence[BaseMessage], operator.add]
    next: str

from core.config import settings

# 1. Sub-Agents Tools
expense_tools = [add_expense, get_recent_expenses]
stock_tools = [add_stock_trade]

llm = ChatGoogleGenerativeAI(
    model="gemini-3.5-flash",
    temperature=0,
    google_api_key="sk-dummy-key"
)

expense_llm = llm.bind_tools(expense_tools)
stock_llm = llm.bind_tools(stock_tools)

expense_tool_node = ToolNode(expense_tools)
stock_tool_node = ToolNode(stock_tools)

# Router Schema


# Nodes
async def router_node(state: AgentState):
    messages = state["messages"]
    response = await llm.ainvoke(
        [{"role": "system", "content": "Route the user's request to 'expense_agent' or 'stock_agent'. Reply ONLY with 'expense_agent' or 'stock_agent'."}] + messages
    )
    text_content = response.content
    if isinstance(text_content, list): text_content = " ".join(m.get("text", "") if isinstance(m, dict) else str(m) for m in text_content)
    text = text_content.strip().lower()
    if "expense" in text: return {"next": "expense_agent"}
    return {"next": "stock_agent"}

async def expense_agent_node(state: AgentState):
    messages = state["messages"]
    response = await expense_llm.ainvoke(messages)
    return {"messages": [response]}

async def stock_agent_node(state: AgentState):
    messages = state["messages"]
    response = await stock_llm.ainvoke(messages)
    return {"messages": [response]}

# Define Graph
workflow = StateGraph(AgentState)

workflow.add_node("router", router_node)
workflow.add_node("expense_agent", expense_agent_node)
workflow.add_node("stock_agent", stock_agent_node)
workflow.add_node("expense_tools", expense_tool_node)
workflow.add_node("stock_tools", stock_tool_node)

workflow.set_entry_point("router")

# Routing logic
def route_to_agent(state: AgentState):
    return state["next"]

workflow.add_conditional_edges("router", route_to_agent, {
    "expense_agent": "expense_agent",
    "stock_agent": "stock_agent"
})

# Sub-agent tool routing
def expense_should_continue(state: AgentState):
    last_message = state["messages"][-1]
    if last_message.tool_calls:
        return "expense_tools"
    return END

def stock_should_continue(state: AgentState):
    last_message = state["messages"][-1]
    if last_message.tool_calls:
        return "stock_tools"
    return END

workflow.add_conditional_edges("expense_agent", expense_should_continue, {
    "expense_tools": "expense_tools",
    END: END
})

workflow.add_conditional_edges("stock_agent", stock_should_continue, {
    "stock_tools": "stock_tools",
    END: END
})

workflow.add_edge("expense_tools", "expense_agent")
workflow.add_edge("stock_tools", "stock_agent")

def get_compiled_graph(saver: AsyncPostgresSaver = None):
    return workflow.compile(checkpointer=saver)
