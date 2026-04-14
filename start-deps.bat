@echo off
echo Starting AgentsProj dependencies...

docker-compose up -d

echo.
echo Waiting for services to be ready...
timeout /t 30 /nobreak > nul

echo.
echo Services status:
docker-compose ps

echo.
echo All services started!
echo - MySQL: localhost:3306 (root / xiao_hei20050512)
echo - Redis: localhost:6380
echo - RocketMQ NameServer: localhost:9876
echo - RocketMQ Broker: localhost:10911
echo - GitHub MCP Server: http://localhost:3000

pause
