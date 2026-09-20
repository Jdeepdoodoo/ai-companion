from fastapi import APIRouter, Depends
from fastapi.responses import HTMLResponse, PlainTextResponse
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select

from db.database import get_db
from db.models import User, AgentRun
from api.dependencies import require_admin
from agents.graph import workflow

router = APIRouter(tags=["console"])

CONSOLE_HTML = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Hydra Station - Agent Console</title>
    <script type="module">
        import mermaid from 'https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.esm.min.mjs';
        mermaid.initialize({ startOnLoad: true });
        
        async function loadGraph() {
            const token = localStorage.getItem("token");
            const res = await fetch("/api/agent-graph.mmd", {
                headers: { "Authorization": `Bearer ${token}` }
            });
            if (res.ok) {
                const text = await res.text();
                const container = document.getElementById("mermaid-container");
                container.innerHTML = `<pre class="mermaid">${text}</pre>`;
                mermaid.init(undefined, document.querySelectorAll('.mermaid'));
            } else {
                document.getElementById("mermaid-container").innerText = "Failed to load graph. Are you admin?";
            }
        }
        
        async function loadRuns() {
            const token = localStorage.getItem("token");
            const res = await fetch("/api/agent-runs", {
                headers: { "Authorization": `Bearer ${token}` }
            });
            if (res.ok) {
                const runs = await res.json();
                const tbody = document.getElementById("runs-body");
                tbody.innerHTML = "";
                runs.forEach(run => {
                    const tr = document.createElement("tr");
                    tr.innerHTML = `
                        <td>${run.id}</td>
                        <td>${run.agent_name}</td>
                        <td>${run.status}</td>
                        <td>${JSON.stringify(run.input_data)}</td>
                        <td>${run.output_data ? JSON.stringify(run.output_data) : ''}</td>
                        <td>${run.error_message || ''}</td>
                        <td>${run.created_at}</td>
                        <td>${run.completed_at || ''}</td>
                    `;
                    tbody.appendChild(tr);
                });
            } else {
                document.getElementById("runs-container").innerText = "Failed to load runs.";
            }
        }

        window.onload = () => {
            loadGraph();
            loadRuns();
        };
    </script>
    <style>
        body { font-family: sans-serif; padding: 20px; }
        table { border-collapse: collapse; width: 100%; margin-top: 20px; }
        th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
        th { background-color: #f2f2f2; }
    </style>
</head>
<body>
    <h1>Hydra Station Agent Console</h1>
    <h2>Agent Workflow Graph</h2>
    <div id="mermaid-container">Loading...</div>
    
    <h2>Recent Agent Runs</h2>
    <div id="runs-container">
        <table>
            <thead>
                <tr>
                    <th>ID</th>
                    <th>Agent</th>
                    <th>Status</th>
                    <th>Input</th>
                    <th>Output</th>
                    <th>Error</th>
                    <th>Created At</th>
                    <th>Completed At</th>
                </tr>
            </thead>
            <tbody id="runs-body">
            </tbody>
        </table>
    </div>
</body>
</html>
"""

@router.get("/console", response_class=HTMLResponse)
async def get_console(current_admin: User = Depends(require_admin)):
    """Serves the static Agent Console UI. Protected by admin check."""
    return HTMLResponse(content=CONSOLE_HTML)

@router.get("/api/agent-graph.mmd", response_class=PlainTextResponse)
async def get_agent_graph(current_admin: User = Depends(require_admin)):
    """Returns the mermaid.js representation of the LangGraph workflow."""
    return workflow.get_graph(xray=True).draw_mermaid()

@router.get("/api/agent-runs")
async def get_agent_runs(limit: int = 50, session: AsyncSession = Depends(get_db), current_admin: User = Depends(require_admin)):
    """Returns recent agent runs for the console."""
    result = await session.execute(
        select(AgentRun).order_by(AgentRun.created_at.desc()).limit(limit)
    )
    runs = result.scalars().all()
    return runs
