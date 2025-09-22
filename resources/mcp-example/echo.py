#to run this python script pip install mcp
import asyncio
import json
from mcp.server import Server
from mcp.server.stdio import stdio_server
from mcp.types import Tool, TextContent

app = Server("echo-server")

@app.list_tools()
async def list_tools() -> list[Tool]:
    return [
        Tool(
            name="echo",
            description="Echo back the message you provide",
            inputSchema={
                "type": "object",
                "properties": {
                    "message": {
                        "type": "string",
                        "description": "The message to echo back"
                    }
                },
                "required": ["message"]
            }
        ),
        Tool(
            name="echo_multiple",
            description="Echo back multiple arguments as a formatted string",
            inputSchema={
                "type": "object",
                "properties": {
                    "arg1": {
                        "type": "string",
                        "description": "First argument"
                    },
                    "arg2": {
                        "type": "string",
                        "description": "Second argument"
                    },
                    "arg3": {
                        "type": "string",
                        "description": "Third argument (optional)"
                    }
                },
                "required": ["arg1", "arg2"]
            }
        ),
        Tool(
            name="echo_json",
            description="Echo back all arguments as JSON",
            inputSchema={
                "type": "object",
                "properties": {},
                "additionalProperties": True
            }
        )
    ]

@app.call_tool()
async def call_tool(name: str, arguments: dict) -> list[TextContent]:
    if name == "echo":
        return [TextContent(type="text", text=arguments["message"])]
    
    elif name == "echo_multiple":
        result = f"arg1: {arguments['arg1']}, arg2: {arguments['arg2']}"
        if "arg3" in arguments:
            result += f", arg3: {arguments['arg3']}"
        return [TextContent(type="text", text=result)]
    
    elif name == "echo_json":
        return [TextContent(type="text", text=json.dumps(arguments, indent=2))]
    
    raise ValueError(f"Unknown tool: {name}")

async def main():
    async with stdio_server() as (read_stream, write_stream):
        await app.run(read_stream, write_stream, app.create_initialization_options())

if __name__ == "__main__":
    asyncio.run(main())
