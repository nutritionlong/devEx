## DevEx Service Quick Start Guide

Welcome! The goal of this guide is to get you to build all local services and start developing in under 30 minutes, completely saying goodbye to the "local environment setup pain."

#### Our Solution

- **EnvDoctor (Environment Doctor):** A built-in Java tool to check all your dependency versions with one click, solving the "inconsistent environments" pain point.
- **Docker Compose (One-Click Start):** A configuration file to orchestrate all microservices and databases, solving the "local database/service is error-prone" pain point.

#### Step 1: Prerequisites

Before you begin, please ensure you have cloned the project repository and installed the following tools.

**1. Software Requirements**

- Java 21
- Maven 3.9+
- Docker 24+
- Git 2.x+

**2. Ports & Credentials**

⚠️ **Important:** Please ensure the following ports are not in use on your local machine:

- `3306`: MySQL Database
- `6379`: Redis Cache
- `8080`: live-platform service (Core platform) simulates the core service
- `8081`: live-bill service
- `8082`: live-gift service

**Database Credentials (auto-created by Docker Compose):**

- **Database Name:** `devex`
- **User:** `root`
- **Password:** `123456`
- **Host:** `localhost`

#### Step 2: Run the Environment Doctor (EnvDoctor)

First, let's verify that your local environment meets all requirements.

- **Function:** Checks if your Java, Git, Maven, and Docker versions are correct.

- **How to Run:**

    - **Method (Recommended - IDE):** `EnvDoctor` is in the `live-platform` module. Navigate to `org.example.liveplatform.EnvDoctor`, right-click, and select "Run 'EnvDoctor.main()'".

- **Expected Output:**

  ```
  ----------------- SUMMARY -----------------
  [Java]     version=21.0.8+12-LTS-250 (major=21)  [OK] (>= 21)
  [Git]      present=true, version=2.50.1 (major=2)  [OK] (>= 2)
  [Maven]    present=true, version=3.9.11 (...)   [OK]
  [Docker]   present=true, version=28.5.1 (major=28)  [OK] (>= 24)
  daemon running=true   
  [Compose]  present=true, version=2.40.3
  -------------------------------------------
  ```

If all checks show `[OK]`, you are ready for the next step!

#### Step 3: One-Click Start for All Services (Docker Compose)

This is the most critical step. We will use Docker Compose to build images for all services and start the database, cache, and all microservices.

1. **Start Docker:** Please ensure your Docker Desktop is running.

2. **Run Compose Command:** In the project's root directory (`devEx` directory), execute the following command:

   ```
   docker compose up --build -d
   ```

    - `--build`: Tells Compose to build the `live-platform`, `live-bill`, and `live-gift` Docker images before starting.
    - `-d`: Runs all containers in the background (detached mode).

💡 **First time running?**

> The first execution will take a few minutes, as it needs to download the base images for MySQL and Redis and compile all your Java applications.

- **Expected Output:**

  ```
  ✔ Network devex_microservice-net       Created
  ✔ Volume devex_redis-data            Created
  ✔ Volume devex_mysql-data            Created
  ✔ Container mysql-db                 Started
  ✔ Container redis-cache              Started
  ✔ Building live-bill-image           ...
  ✔ Building live-gift-image           ...
  ✔ Building live-platform-image       ...
  ✔ Container live-bill-app-container    Started
  ✔ Container live-gift-app-container    Started
  ✔ Container live-platform-app-container Started
  ```

#### Step 4: Verify All Services

Let's confirm that everything is running as expected.

**1. Check Container Status**

Run `docker ps` to see all running containers:

- **Expected Output:** (Note: STATUS should be `Up ... (healthy)` or `Up ...`)

  ```
  CONTAINER ID   IMAGE                        COMMAND                    STATUS                  PORTS
  bb15f364b0e5   live-bill-image:latest       "java -jar /app/app.…"     Up 1 minute             0.0.0.0:8081->8080/tcp
  d8ca89699d2c   live-gift-image:latest       "java -jar /app/app.…"     Up 1 minute             0.0.0.0:8082->8080/tcp
  4bfc5266bad8   live-platform-image:latest   "java -jar /app/app.…"     Up 1 minute             0.0.0.0:8080->8080/tcp
  7cdc0e1fdfae   redis:latest                 "docker-entrypoint.s…"     Up 1 minute (healthy)   0.0.0.0:6379->6379/tcp
  80116e3cfca8   mysql:8.0                    "docker-entrypoint.s…"     Up 1 minute (healthy)   0.0.0.0:3306->3306/tcp
  ```

**2. Test Service APIs**

You can now access your microservices directly via `localhost`.

- **Test live-platform (Get user data):**

  ```
   http://localhost:8080/api/users/1
  ```

  **Response:**

  ```
  {
    "id": 1,
    "firstName": "Alice",
    "lastName": "Wang",
    "email": "alice.wang@example.com",
    ...
  }
  ```

- **Test live-bill (Health check):**

  ```
   http://localhost:8081/hello
  ```

  **Response:**

  ```
  hello live-bill
  ```

- **Test live-gift (Health check):**

  ```
   http://localhost:8082/hello
  ```

  **Response:**

  ```
  hello live-gift
  ```

**dataSeeder: Data Initialization** If you query the database, you can see that the `user` table has been initialized with basic data. This prevents errors from users accessing an empty table.

#### Congratulations! You are ready to develop!

Your local microservice environment (including database and cache) is 100% running.

##### What's Next?

**Connect to the Database:**

- Use your favorite database client (e.g., DBeaver, DataGrip) to connect to `localhost:3306`.
- **Database:** `devex`
- **User:** `root`
- **Password:** `123456`
- You can now see the `users` table and the initialized data.

**Common Docker Commands:**

- **Stop all services:** `docker compose down` (Stops and removes all containers)
- **Start only the database:** `docker compose up -d mysql-db`
- **View logs:** `docker compose logs -f live-platform-app`

#### Common Issues (Troubleshooting)

**1. Error: `Error starting userland proxy: listen tcp 0.0.0.0:3306: bind: address already in use`**

- **Cause:** Port `3306` (or `8080`/`8081`/`8082`) is already in use by another process on your local machine.
- **Solution:**
    1. Find and stop the process using that port (e.g., you might have another MySQL instance running locally).
    2. Or, modify the `docker-compose.yml` file to change the `ports` mapping to another port. For example, change `"3306:3306"` to `"3307:3306"`.

**2. Error: `docker compose build` fails**

- **Cause:** The Docker daemon may not be running, or the Maven build failed.
- **Solution:**
    1. Ensure Docker Desktop is running.
    2. Try running `mvn clean package` locally first (before the Docker Compose command) to ensure all Java modules compile successfully.
    3. Try running `docker compose build --no-cache` to force a rebuild.

**3. Error: `Container ... is unhealthy`**

- **Cause:** The database or cache failed to start.
- **Solution:**
    1. Run `docker compose logs mysql-db` or `docker compose logs redis-cache` to see the specific error logs.
    2. The most common reasons are Docker Volume permission issues or insufficient disk space.