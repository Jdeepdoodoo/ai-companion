import json
import os
from typing import Protocol, List
from datetime import datetime

class BackupProvider(Protocol):
    async def export_snapshot(self, data: dict) -> None:
        """Exports a dictionary representing the database snapshot."""
        ...
        
    async def list_backups(self) -> List[str]:
        """Lists available backup IDs (e.g. filenames)."""
        ...
        
    async def restore(self, backup_id: str) -> dict:
        """Loads a backup snapshot by ID."""
        ...

class LocalFileBackupProvider:
    def __init__(self, backup_dir: str = "/app/backup/data"):
        self.backup_dir = backup_dir
        os.makedirs(self.backup_dir, exist_ok=True)
        
    async def export_snapshot(self, data: dict) -> None:
        timestamp = datetime.utcnow().strftime("%Y%m%d_%H%M%S")
        filename = f"backup_{timestamp}.json"
        filepath = os.path.join(self.backup_dir, filename)
        
        with open(filepath, 'w') as f:
            json.dump(data, f, indent=2, default=str)
            
    async def list_backups(self) -> List[str]:
        if not os.path.exists(self.backup_dir):
            return []
        return sorted([f for f in os.listdir(self.backup_dir) if f.endswith('.json')])
        
    async def restore(self, backup_id: str) -> dict:
        filepath = os.path.join(self.backup_dir, backup_id)
        if not os.path.exists(filepath):
            raise FileNotFoundError(f"Backup {backup_id} not found.")
        with open(filepath, 'r') as f:
            return json.load(f)
