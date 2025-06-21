#!/usr/bin/env python3
"""
Comprehensive test suite for JMX MCP Server
Tests MCP protocol compliance, tool functionality, and resource discovery
"""

import json
import subprocess
import sys
import time
import signal
import os
from typing import Dict, Any, Optional

class MCPTester:
    def __init__(self):
        self.server_process = None
        self.test_results = []
        
    def start_server(self) -> bool:
        """Start the JMX MCP Server"""
        try:
            jar_path = "target/jmx-mcp-server-1.0.0.jar"
            if not os.path.exists(jar_path):
                print(f"❌ JAR file not found: {jar_path}")
                print("   Run 'mvn clean package' first")
                return False
                
            cmd = [
                "java",
                "-Xmx512m",
                "-Xms256m", 
                "-Dspring.profiles.active=stdio",
                "-Dspring.main.banner-mode=off",
                "-Dlogging.level.root=ERROR",
                "-Dspring.main.log-startup-info=false",
                "-jar", jar_path
            ]
            
            self.server_process = subprocess.Popen(
                cmd,
                stdin=subprocess.PIPE,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                bufsize=0
            )
            
            # Wait for server to start
            time.sleep(3)
            
            if self.server_process.poll() is not None:
                stderr = self.server_process.stderr.read()
                print(f"❌ Server failed to start: {stderr}")
                return False
                
            print("✅ JMX MCP Server started successfully")
            return True
            
        except Exception as e:
            print(f"❌ Failed to start server: {e}")
            return False
    
    def stop_server(self):
        """Stop the JMX MCP Server"""
        if self.server_process:
            self.server_process.terminate()
            try:
                self.server_process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                self.server_process.kill()
            print("✅ Server stopped")
    
    def send_request(self, request: Dict[str, Any]) -> Optional[Dict[str, Any]]:
        """Send a JSON-RPC request to the server"""
        try:
            request_json = json.dumps(request) + "\n"
            self.server_process.stdin.write(request_json)
            self.server_process.stdin.flush()
            
            # Read response
            response_line = self.server_process.stdout.readline()
            if not response_line:
                return None
                
            return json.loads(response_line.strip())
            
        except Exception as e:
            print(f"❌ Request failed: {e}")
            return None
    
    def test_initialize(self) -> bool:
        """Test MCP initialization"""
        print("\n🔧 Testing MCP Initialization...")
        
        request = {
            "jsonrpc": "2.0",
            "id": 1,
            "method": "initialize",
            "params": {
                "protocolVersion": "2024-11-05",
                "capabilities": {
                    "tools": {},
                    "resources": {}
                },
                "clientInfo": {
                    "name": "test-client",
                    "version": "1.0.0"
                }
            }
        }
        
        response = self.send_request(request)
        if not response:
            print("❌ No response to initialize")
            return False
            
        if "result" not in response:
            print(f"❌ Initialize failed: {response}")
            return False
            
        result = response["result"]
        if "capabilities" not in result:
            print("❌ No capabilities in initialize response")
            return False
            
        print("✅ MCP initialization successful")
        return True
    
    def test_list_tools(self) -> bool:
        """Test tools listing"""
        print("\n🔧 Testing Tools Listing...")
        
        request = {
            "jsonrpc": "2.0",
            "id": 2,
            "method": "tools/list"
        }
        
        response = self.send_request(request)
        if not response or "result" not in response:
            print("❌ Failed to list tools")
            return False
            
        tools = response["result"].get("tools", [])
        expected_tools = ["listMBeans", "getMBeanInfo", "getAttribute", "setAttribute", "listDomains", "getConnectionInfo"]
        
        found_tools = [tool["name"] for tool in tools]
        missing_tools = [tool for tool in expected_tools if tool not in found_tools]
        
        if missing_tools:
            print(f"❌ Missing tools: {missing_tools}")
            return False
            
        print(f"✅ Found {len(tools)} tools: {found_tools}")
        return True
    
    def test_list_resources(self) -> bool:
        """Test resources listing"""
        print("\n🔧 Testing Resources Listing...")
        
        request = {
            "jsonrpc": "2.0",
            "id": 3,
            "method": "resources/list"
        }
        
        response = self.send_request(request)
        if not response or "result" not in response:
            print("❌ Failed to list resources")
            return False
            
        resources = response["result"].get("resources", [])
        
        if len(resources) == 0:
            print("❌ No resources found")
            return False
            
        print(f"✅ Found {len(resources)} resources")
        return True
    
    def test_tool_execution(self) -> bool:
        """Test tool execution"""
        print("\n🔧 Testing Tool Execution...")
        
        # Test listDomains tool
        request = {
            "jsonrpc": "2.0",
            "id": 4,
            "method": "tools/call",
            "params": {
                "name": "listDomains"
            }
        }
        
        response = self.send_request(request)
        if not response or "result" not in response:
            print("❌ Failed to execute listDomains tool")
            return False
            
        content = response["result"].get("content", [])
        if not content:
            print("❌ No content in tool response")
            return False
            
        print("✅ Tool execution successful")
        return True
    
    def run_all_tests(self) -> bool:
        """Run all tests"""
        print("🚀 Starting JMX MCP Server Comprehensive Tests")
        print("=" * 50)
        
        if not self.start_server():
            return False
            
        try:
            tests = [
                self.test_initialize,
                self.test_list_tools,
                self.test_list_resources,
                self.test_tool_execution
            ]
            
            passed = 0
            total = len(tests)
            
            for test in tests:
                if test():
                    passed += 1
                else:
                    break
                    
            print("\n" + "=" * 50)
            print(f"📊 Test Results: {passed}/{total} tests passed")
            
            if passed == total:
                print("🎉 All tests passed! JMX MCP Server is ready for deployment.")
                return True
            else:
                print("❌ Some tests failed. Please check the issues above.")
                return False
                
        finally:
            self.stop_server()

def main():
    """Main test runner"""
    if len(sys.argv) > 1 and sys.argv[1] == "--help":
        print("JMX MCP Server Comprehensive Test Suite")
        print("Usage: python3 comprehensive-test.py")
        print("\nThis script tests:")
        print("- MCP protocol compliance")
        print("- Tool registration and execution")
        print("- Resource discovery")
        print("- JSON-RPC communication")
        return
        
    tester = MCPTester()
    
    def signal_handler(sig, frame):
        print("\n🛑 Test interrupted")
        tester.stop_server()
        sys.exit(1)
        
    signal.signal(signal.SIGINT, signal_handler)
    
    success = tester.run_all_tests()
    sys.exit(0 if success else 1)

if __name__ == "__main__":
    main()
