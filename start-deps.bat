@echo off
echo Starting AgentsProj dependencies...

docker compose up -d mysql redis rmqnamesrv rmqbroker

echo.
echo Waiting for services to be ready...
timeout /t 30 /nobreak > nul

echo.
echo Services status:
docker compose ps

echo.
echo All services started!
echo - MySQL: localhost:3306 (password comes from MYSQL_PASSWORD or docker-compose default)
echo - Redis: localhost:6380
echo - RocketMQ NameServer: localhost:9876
echo - RocketMQ Broker: localhost:10911

pause
