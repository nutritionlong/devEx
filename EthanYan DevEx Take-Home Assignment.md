

## **DevEx Take-Home Assignment    **Solution: 30-Minute Local Setup



### 1. What Problem Are We Actually Solving? (Problem Framing)

Based on my understanding of this problem and my past decade of engineering experience, when teams complain that "Onboarding takes too much time" and "The local environment is painful," it is almost always a "systemic" problem, not just a "technical" one. I completely understand this frustration—it drains the enthusiasm of new colleagues and consumes the time of senior engineers.

From a technical perspective, these pain points can be summarized in three parts:

**Pain Point 1: Disconnect between Process and Tools, Leading to a "Slow Ramp-Up"**

- **Problem:** A "slow ramp-up" stems from the disconnect between processes and tools. On the process side, new colleagues only start applying for a pile of accounts like Git, Jira, and AWS on their first day. This approval process wastes several days. On the tools side, we rely on "manual operations," requiring engineers to spend a lot of time installing software, configuring `settings.xml`, and setting environment variables.
- **My Experience:** We must treat the "environment" as a "product" to be delivered.
  - **For Accounts:** Permissions should be fully configured *before* the employee's start date, not as their first task *after* they join.
  - **For the Environment:** I would pre-fabricate `docker-compose.yml` files for all dependencies (like databases, caches). First, run an environment diagnostic (e.g., call EnvDoctor), then use `docker-compose up` to spin up all required services with one command, and automatically invoke a `dataSeeder` script to complete the initial data insertion.

**Pain Point 2: The Core Bottleneck—The Database that "Won't Run" and Has "Stale Data"**

- **Problem:** "The local database won't run" is just the symptom. Even if it does run, an empty database with only the initial schema is useless. What engineers need is data that is close to the production environment for effective debugging.
- **My Experience:** In my previous team, we also encountered the problem of the remote database being "read-only," which made it impossible to debug "write" operations locally. My solution was to write a script (e.g., `db.refresh.sh`) that, when run with one command, would automatically pull an (anonymized) data snapshot from the remote database and load it into the local Docker database. When an engineer needed the latest data, they just ran this script, and within minutes, their local environment was 100% refreshed.
- Currently, I use a `dataSeeder` to initialize some key data and use Flyway to synchronize the database table version status, ensuring that the service doesn't error out due to table structure or missing data upon startup.

**Pain Point 3: Fragile and Inefficient "Cross-Service Testing"**

- **Problem:** "Inconsistent testing" mainly refers to E2E (End-to-End) testing. Unit Tests and Integration Tests target single services and are not the problem. The issue with E2E is that as soon as a service I depend on is updated, the entire flow can fail, and the problem is discovered too late.
- **My Experience:** First, we must accept reality: the very purpose of E2E testing is to expose this kind of cross-service breaking change, so it is "fragile" by nature.
- **Solution:** Don't reproduce it locally. Instead, achieve fully automated E2E testing in the CI/CD pipeline. In my practice: we would set a rule: as soon as any team's PR (Pull Request) is merged into `dev`, it immediately triggers the complete E2E test suite. If the test fails, the entire pipeline turns red, and everyone immediately knows which service's change caused the problem. This can shorten the time to discovery from days to minutes.

**Supplementary Thought: A Parallel Option—A "Hybrid" Remote Development Environment**

While analyzing the above pain points, I also considered another solution: establishing a "hybrid" remote development environment.

This model looks like this:

- **Remote (Server):** We maintain one (or more) shared, pre-configured development servers. All microservices, monoliths, and databases are already deployed on it.
- **Local (Engineer):** The engineer only needs to install basic development tools (like JDK, Maven, IDE) locally.
- When the engineer needs to develop, they connect via VPN and *only run the one service they are currently modifying* locally. This local service is configured to connect to the shared database and other shared microservices on the remote server.

The pros and cons of this solution are very extreme:

- **Pros:**
  - **Extremely Fast Ramp-Up:** This is the biggest benefit. New employees barely need to "set up an environment" and can start debugging code within minutes.
  - **Extremely Low Local Resource Consumption:** The engineer's laptop no longer needs to run 10+ containers and databases.
- **Cons:**
  - **"Dirty Data" and Environment Interference:** This is the core problem you pointed out: the data is not isolated and is affected by others. If Engineer A is debugging a delete function, Engineer B (connected to the same shared database) might find their data suddenly disappearing.
  - **Network Dependency and Debugging Complexity:** The entire development flow is highly dependent on the VPN. Even more complex is, how does a remote service call the service I'm running locally? This usually requires additional tools to establish a reverse proxy, introducing a new learning curve.

**[Conclusion]**

The current proposal mainly solves the first two pain points. For the third point (E2E testing), my solution is to separate it from local development and push it to CI automation.

As for the "hybrid remote development environment," I believe its advantage in "ramp-up speed" is huge, but it sacrifices "environment isolation." And this (data being contaminated by others) is precisely one of the most frustrating pain points for engineers in their daily development.

Therefore, I believe a robust, Docker-based local environment solution (like EnvDoctor + docker-compose) is the most pragmatic first step to solving the team's current "local unreliability" and "stale data" problems, and the one that best guarantees development isolation.

**Our Users and Needs**

Our users are two types of core engineers. Their needs have commonalities but different emphases:

**User 1: New Hires**

- **Need:** They need a "Golden Path." Their biggest desire is to "deliver value quickly" and "gain confidence," not to burn out their enthusiasm on environment configuration.
- **Specific Pain Point:** "I want to be able to run the project and start looking at code on my first day, not spend several days fighting with configuration."

**User 2: Existing/Senior Engineers**

- **Need:** They need an "Isolated & Reliable" environment. Their biggest desires are "stability" and "reproducibility." They don't want their local environment to be a debugging black box.
- **Specific Pain Point:** "I need a 100% reproducible, debuggable environment, and I especially don't want to break other things on my computer just because I installed a new project."

### 2. My Proposed Solution

My idea is simple: we will tackle the problem from four aspects—"People," "Tools," "Trade-offs," and "Adoption"—to solve the problem in a tangible way.

#### A. Optimize the Process (People)

Even the best tool can't save a chaotic process. An empathetic process is the foundation for a successful technical solution.

**1. A "Buddy" and Pre-Provisioned Permissions**

- **Content:** Before the new colleague's Day 1, assign a "Buddy." This partner is responsible for getting all accounts (Git, Slack, Jira, etc.) activated *before* the new colleague arrives.
- **Reason:** Eliminate the frustration and time-wasting of "waiting for permissions." Let the new colleague feel they have an ally from the moment they arrive.

**2. A Clear, Unified "New Engineer's Guide"**

- **Content:** Throw out all the scattered READMEs and Wikis. We only need one unified "New Engineer Onboarding Guide," which includes:
  - **Day 1-2: Set Up.** (Follow the README to get EnvDoctor and Docker Compose running, get familiar with the tools).
  - **Day 3: Architecture.** (Understand how the monolith and the 10 microservices work).
  - **Day 4: Business Context.** (Learn the core business).
  - **Day 5-6: Your First Ticket.** (Pick up a specially prepared, low-risk "first ticket").
- **Reason:** Give new colleagues a clear, low-pressure learning path to help them build confidence step by step.

#### B. Reliable Tools (Tools)

We shouldn't let engineers configure their environment by guessing. Our goal is to provide a set of "out-of-the-box" tools, accompanied by clear documentation, so that everyone can get it done themselves.

**Deliverable 1: `EnvDoctor.java` (The Environment Doctor)**

- A cross-platform diagnostic tool. It doesn't just check version numbers; it can intelligently distinguish between "Docker isn't installed" and "Docker is installed but not running." It can handle various Windows `PATH` issues, check `MAVEN_HOME`, and provide clear, friendly error messages *before* the engineer gets stuck.

**Deliverable 2: `README.md` (The Friendly User Interface)**

- This is the "product" we deliver to engineers. It must be clear, concise, and aimed at getting an engineer up and running in under 30 minutes.

**Deliverable 3: `Dockerfile` (Standardized Application Builds)**

- A reusable, multi-stage build `Dockerfile`. It utilizes layer caching to speed up builds and uses a wildcard (`*.jar`) so all Spring Boot modules can share this one file, greatly reducing maintenance costs.

**Deliverable 4: `docker-compose.yml` (Standardized Environment Reproduction)**

- This is the cornerstone of the solution. It defines all services, monoliths, databases, and caches in a single file. By using `healthcheck` and `depends_on`, we ensure services start in the correct order, completely solving the core pain point of the "error-prone local database."

**Deliverable 5: `db.refresh.sh` (Data Resetter - Pseudocode)**

- A simple bash script. It connects to the local Docker database, `DROP`s and `CREATE`s the database, then runs all schema migrations and seed data. This allows an engineer who has "broken" their local data to restore it to a clean "factory setting" with one command.

#### C. Trade-offs and Reasoning

This "local-first" solution based on Docker Compose, which I am proposing, has the core advantages of providing perfect "environment isolation" and "reproducibility." However, it also introduces new challenges, and we must be honest about these trade-offs:

**Trade-off 1: Local Resource Consumption (The Biggest Trade-off)**

- **Cost:** Requiring all 10+ microservices, the monolith, and databases to run locally places high demands on the engineer's laptop configuration (especially RAM and CPU). Colleagues with lower-spec machines may experience fans spinning loudly and IDE lag.
- **Reasoning:** This is the "hardware cost" we pay in exchange for "environment isolation."

**Trade-off 2: Docker Learning Curve**

- **Cost:** This solution introduces Docker as a "mandatory skill" for all engineers. The team will need time to learn how to install, use, and debug Docker Desktop, such as handling "port conflicts" or "container start-up failures."
- **Reasoning:** `EnvDoctor` and a clear `README` are designed to minimize this barrier to entry as much as possible.

**Trade-off 3: Maintenance Overhead**

- **Cost:** As the number of services increases, the `docker-compose.yml` file itself will become more complex. The `Dockerfile` and base images will also require effort from the platform engineering team (or us) to continuously maintain and optimize for build speed and security.
- **Reasoning:** This "centralized maintenance cost" is far lower than the "decentralized hidden cost" of having 50 engineers each maintain their own local environment.

**Trade-off 4: Data Seeding Limitations**

- **Cost:** The `db.refresh.sh` script is very effective for small amounts of data. But if the "anonymized data snapshot" is tens of gigabytes, pulling and loading it into the local database each time will be very time-consuming, which in turn will impact development efficiency.
- **Reasoning:** This is an engineering problem to be solved. Initially, our `dataSeeder` will only insert "core seed data" to ensure start-up, not the full dataset.

**[My Reasoning]**

Despite these trade-offs (especially resource consumption), this solution addresses the core pain points—"unreliable environments" and "contaminated data"—which are currently the biggest bottlenecks to the team's development efficiency. We are sacrificing "hardware resources" for "stability and isolation," which is a worthy trade-off at this stage.

In contrast, the "hybrid remote development" solution (analyzed in Section 1), while saving resources, brings "environment interference" problems (like 'dirty data') that are devastating to daily debugging.

#### D. Ensuring Adoption

A good tool that nobody uses is a failure. The key to driving adoption is not a top-down mandate, but making the new method obviously better and easier.

My entire strategy is based on one core principle: **We are here to help, not to disrupt.** My approach will be "surgical" and phased:

**1. Validate: Which pain point is the \*most\* painful?**

- Before rolling out any solution, I must first validate which problem is the team's most core, urgent pain point.
- **Scenario 1: If the company has many new hires (high turnover).**
  - Then "Onboarding" is the biggest pain point. My first priority will be `EnvDoctor` and that clear `README.md`. I will use "Time to First Meaningful Commit for new hires" as my core metric.
- **Scenario 2: If the company's staffing is stable (low turnover).**
  - Then "Onboarding" is not the most urgent. The biggest pain point might be "the local DB often fails to run" or "data inconsistency." In this case, my "MVP" (Minimum Viable Product) will focus *only* on that one problem. For example, I would only launch `docker-compose up -d mysql-db` and `db.refresh.sh` first. I would tell the team: "You don't have to change anything. Just use these two commands, and you'll get a 100% clean database in 30 seconds."

**2. Start by "Helping," Build Trust**

- We will start by perfectly solving this one problem. We build trust by "helping," not by creating chaos through "forceful promotion." Once the team (especially senior engineers) finds that this small tool truly saves them time every day, they will naturally develop trust.

**3. Find "Champions" and Show Results**

- After solving the first pain point, I will find the "friendly users" who benefited the most.
- **Let them speak:** One senior engineer saying, "This new Docker solution saved me an hour of debugging time" in Slack is more effective than me saying 100 words.
- **Hold a "Goodbye Pain" Demo:** *Then* we hold a demo session to present the full solution. People won't be resistant because they already believe you are there to help them.

**4. Make it the "Golden Path" for New Hires**

- For new colleagues, we will use this new process by default. They don't have the baggage of old habits, and this will build a good reputation from the ground up.

**5. Listen and Iterate Continuously**

- We will set up a `#devex-help` channel. We will treat every complaint and question as a "bug in the documentation" or a "bug in the tool," not as "the user's fault."

### 3. Code/Prototype

To verify the feasibility of this solution, I simulated the described business scenario by building three microservices (`live-platform`, `live-bill`, `live-gift`), a MySQL database, and a Redis cache service.

The following are the core prototypes of this solution. They directly demonstrate how to solve the two major pain points defined earlier—"slow ramp-up" and "unreliable database"—through "tool standardization."

*(Core Code Snippets)*

#### A. EnvDoctor.java (Snippet )

This is a cross-platform diagnostic tool written in Java. It demonstrates how to provide "intelligent" diagnostics, not just version checks.

```java
public class EnvDoctor {

  // ===== Configuration =====
  static final boolean VERBOSE = true;          // Print detailed diagnostics
  static final int MIN_JAVA_MAJOR   = 21;       // Minimum major version required (modify as needed)
  static final int MIN_GIT_MAJOR    = 2;
  static final int MIN_DOCKER_MAJOR = 25;

  public static void main(String[] args) {
    System.out.println("==================================================");
    System.out.println("Dev Doctor (Java) - Environment Check");
    System.out.println("==================================================");

    // Basic environment
    logBasicEnv();
    logPathEntries();

    // where/which initial diagnostics
    logWhereWhich(osIsWindows() ? "java.exe" : "java");
    logWhereWhich(osIsWindows() ? "git.exe" : "git");
    logWhereWhich(osIsWindows() ? "mvn.cmd" : "mvn");
    logWhereWhich(osIsWindows() ? "docker.exe" : "docker");
    if (osIsWindows()) logWhereWhich("docker-compose.exe");
    else               logWhereWhich("docker-compose");

    // ===== 1) Java =====
    JavaVer jv = detectJavaVersion();
    boolean javaOk = jv.major >= MIN_JAVA_MAJOR;

    // ===== 2) Git =====
    CmdResult git = detectGit();
    int gitMajor = parseMajor(git.version);
    boolean gitOk = git.present && gitMajor >= MIN_GIT_MAJOR;

    // ===== 3) Maven (multi-strategy + Windows shell + absolute path + PATH fallback) =====
    CmdResult mvn = detectMavenVerbose();

    // ===== 4) Docker =====
    CmdResult dockerVersion = runCmd("docker", Arrays.asList("--version"), Duration.ofSeconds(8), VERBOSE);
    int dockerMajor = parseMajor(dockerVersion.version);
    boolean dockerPresent = dockerVersion.present;
    boolean dockerOk = dockerPresent && dockerMajor >= MIN_DOCKER_MAJOR;

    CmdResult dockerInfo = runCmd("docker", Arrays.asList("info"), Duration.ofSeconds(10), VERBOSE);
    boolean dockerRunning = dockerInfo.exitCode == 0;

    // ===== 5) Compose (v2: docker compose; v1: docker-compose) =====
    CmdResult composeV2 = runCmd("docker", Arrays.asList("compose", "version"), Duration.ofSeconds(8), VERBOSE);
    CmdResult composeV1 = runCmd(osIsWindows() ? "docker-compose.exe" : "docker-compose",
        Arrays.asList("--version"), Duration.ofSeconds(8), VERBOSE);
    boolean composePresent = composeV2.present || composeV1.present;
    String composeVersion = composeV2.present ? composeV2.version : composeV1.version;
  }
}
```

#### B. docker-compose.yml (Snippet)

This is the cornerstone of the solution. It defines "a reproducible, magic-free local environment." Note the use of `depends_on` and `healthcheck`.

```yaml
version: "3.8"

services:
  # ------------------------------------------
  # Application Services
  # ------------------------------------------

  # 1. Spring Boot "live-platform" Application (Core Service)
  live-platform-app:
    # build context points to the ./live-platform directory
    # It will automatically find the Dockerfile in that directory
    build: ./live-platform
    image: live-platform-image:latest
    container_name: live-platform-app-container
    restart: on-failure
    ports:
      # Core service typically uses port 8080
      - "8080:8080"
    networks:
      - microservice-net
    env_file:
      - ./.env
    environment:
      # Consistent environment variables, all read from .env
      SPRING_DATASOURCE_URL: ${DB_URL}
      SPRING_DATASOURCE_USERNAME: ${DB_USER} # <-- FIXED (was ${DB_NAME})
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD}
      SPRING_REDIS_HOST: ${REDIS_HOST}
      SPRING_REDIS_PORT: ${REDIS_PORT}
    depends_on:
      mysql-db:
        condition: service_healthy
      redis-cache:
        condition: service_healthy

  # 4. MySQL 8.0
  mysql-db:
    image: mysql:8.0
    container_name: mysql-db
    restart: unless-stopped
    env_file:
      - ./.env
    environment:
      # Read configuration from .env file
      MYSQL_ROOT_PASSWORD: ${DB_PASSWORD} # <--- From .env
      MYSQL_DATABASE: ${DB_NAME}        # <--- From .env
    ports:
      # Map host's port 3306 to container's port 3306
      - "3306:3306"
    volumes:
      # Mount a named volume for persistent database data
      - mysql-data:/var/lib/mysql
    networks:
      - microservice-net
    healthcheck:
      # This command correctly uses the MYSQL_ROOT_PASSWORD variable
      # from the container's environment (which is set from the .env file)
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-u", "root", "-p${MYSQL_ROOT_PASSWORD}"]
      interval: 10s
      timeout: 5s
      retries: 5
    command: --default-time-zone=Pacific/Auckland
    
# Top-level network definition
networks:
  microservice-net:
    driver: bridge

# Top-level volume definitions
volumes:
  mysql-data:
  redis-data:
```



### 4. Measuring Success & Gathering Feedback (Measurement & Impact)

A successful DevEx solution must be measurable. We will evaluate the solution's value from both "quantitative metrics" and "qualitative feedback" to establish a continuous improvement feedback loop.

#### A. How to Measure Success

We will replace "feelings" with traceable data metrics, setting a clear "Baseline" (before improvement) and "Success Target" for each metric.

| **Dimension**              | **Key Metric**                      | **How to Measure**                                           | **Current Baseline** | **Success Target**              |
| -------------------------- | ----------------------------------- | ------------------------------------------------------------ | -------------------- | ------------------------------- |
| **Onboarding Efficiency**  | Time to First Meaningful Commit     | (Timestamp of new hire's first PR merged to `dev`) - (New hire's start date) | Avg. X business days | < 4 business hours              |
| **Environment Stability**  | DevEx Support Ticket Volume         | Count of weekly tickets tagged as `local-env` or `db-issue` in the `#devex-help` channel. (Source: Slack / Jira) | Avg. X tickets/week  | 80% reduction, < X tickets/week |
| **Daily Efficiency**       | Time to "Ready-to-Code"             | Measure time from running `docker-compose up` until all services are healthy and the engineer can start debugging. (Source: Script timer) | X minutes (manual)   | < 90 seconds (automated)        |
| **Developer Satisfaction** | Developer Satisfaction (DSAT) Score | Quarterly anonymous survey question: "How satisfied are you with the local development environment? (1-5)" (Source: Survey) | Avg. score X / 5.0   | Avg. score > 4.5 / 5.0          |

#### B. How to Gather Feedback

Data tells us *what* happened, while qualitative feedback tells us *why* it happened. We will establish a continuous feedback loop.

**1. Proactive Loop (We ask them):**

- **New Hire Onboarding Retrospectives:** In the new hire's second week, their "Buddy" will host a 30-minute 1:1 meeting. This is the golden opportunity to get firsthand feedback, specifically collecting every step where they got stuck in the "New Engineer's Guide."
- **Quarterly Anonymous Surveys:** This is the primary way to get quantitative data and anonymous comments. We will analyze keywords in the comments to discover the next biggest pain point.

**2. Reactive Loop (They come to us):**

- **`#devex-help` Slack Channel:** This is our most important source of real-time feedback. Our principle: **"Every question is a bug in the documentation or the tool, not the user's fault."** We will track these issues and use them as our iteration backlog.
- **Informal Interviews with "Champions":** Regularly conduct informal chats with those senior engineers (our "friendly users"). They can provide deeper insights and help promote the solution within the team.



## 5. Documentation (README.md)

### DevEx Service Quickstart Guide

Welcome! The goal of this guide is to get you set up with all local services and start developing within **30 minutes**, putting a permanent end to the "pain of local environment setup."

#### Our Solution

- **EnvDoctor:** A built-in Java tool to check all your dependency versions with one command, solving the "inconsistent environments" pain point.
- **Docker Compose:** A configuration file to orchestrate all microservices and databases, solving the "error-prone local database/service" pain point.

### Step 1: Prerequisites

Before you begin, please ensure you have cloned the project repository and installed the following tools.

#### 1. Software Requirements

- Java 21
- Maven 3.9+
- Docker 24+
- Git 2.x+

#### 2. Ports & Credentials

⚠️ **Important:** Please ensure the following ports are free on your local machine:

- `3306`: MySQL Database
- `6379`: Redis Cache
- `8080`: `live-platform` service (Core platform) - *Simulates the core service*
- `8081`: `live-bill` service
- `8082`: `live-gift` service

**Database Credentials (auto-created by Docker Compose):**

- **Database:** `devex`
- **User:** `root`
- **Password:** `123456`
- **Host:** `localhost`

### Step 2: Run the Environment Doctor (EnvDoctor)

First, let's verify that your local environment meets all requirements.

- **Function:** Checks that your Java, Git, Maven, and Docker versions are correct.
- **How to Run:**
  - **Way 1 (Recommended - IDE):** `EnvDoctor` is in the `live-platform` module. Navigate to `org.example.liveplatform.EnvDoctor`, right-click, and select "Run 'EnvDoctor.main()'".
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

### Step 3: One-Click Start for All Services (Docker Compose)

This is the most critical step. We will use Docker Compose to build images for all services and start the database, cache, and all microservices.

1. **Start Docker:** Please ensure your Docker Desktop is running.
2. **Run the Compose Command:** In the project's root directory (the `devEx` directory), execute the following command:

Bash

```
docker compose up --build -d
```

- `--build`: Tells Compose to build the Docker images for `live-platform`, `live-bill`, and `live-gift` before starting.
- `-d`: Runs all containers in the background (detached mode).

💡 First time running this?

The first execution will take a few minutes as it needs to download the base images for MySQL and Redis, and compile all your Java applications.

- **Expected Output:**

```
✔ Network devex_microservice-net      Created
✔ Volume devex_redis-data           Created
✔ Volume devex_mysql-data           Created
✔ Container mysql-db                Started
✔ Container redis-cache             Started
✔ Building live-bill-image          ...
✔ Building live-gift-image          ...
✔ Building live-platform-image      ...
✔ Container live-bill-app-container   Started
✔ Container live-gift-app-container   Started
✔ Container live-platform-app-container Started
```

### Step 4: Verify All Services

Let's confirm everything is running as expected.

#### 1. Check Container Status

Run `docker ps` to see all running containers:

- **Expected Output:** (Note: `STATUS` should be `Up ... (healthy)` or `Up ...`)

```
CONTAINER ID   IMAGE                        COMMAND                  STATUS                  PORTS
bb15f364b0e5   live-bill-image:latest       "java -jar /app/app.…"   Up 1 minute             0.0.0.0:8081->8080/tcp
d8ca89699d2c   live-gift-image:latest       "java -jar /app/app.…"   Up 1 minute             0.0.0.0:8082->8080/tcp
4bfc5266bad8   live-platform-image:latest   "java -jar /app/app.…"   Up 1 minute             0.0.0.0:8080->8080/tcp
7cdc0e1fdfae   redis:latest                 "docker-entrypoint.s…"   Up 1 minute (healthy)   0.0.0.0:6379->6379/tcp
80116e3cfca8   mysql:8.0                    "docker-entrypoint.s…"   Up 1 minute (healthy)   0.0.0.0:3306->3306/tcp
```

#### 2. Test Service APIs

You can now access your microservices directly via `localhost`.

- **Test `live-platform` (Get user data):**

```
 http://localhost:8080/api/users/1
```

- **Response:**

```
{
  "id": 1,
  "firstName": "Alice",
  "lastName": "Wang",
  "email": "alice.wang@example.com",
  ...
}
```

- **Test `live-bill` (Health check):**

```
 http://localhost:8081/hello
```

- Response:

 ` hello live-bill`

- **Test `live-gift` (Health check):**

```
 http://localhost:8082/hello
```

- Response:

 ` hello live-gift`

dataSeeder: Data Initialization

If you query the database, you will see that the user table has already been initialized with basic data. This prevents errors when users access an empty table.

### Congratulations! You Are Ready to Develop!

Your local microservice environment (including the database and cache) is 100% running.

#### What's Next?

- **Connect to the Database:**
  - Use your favorite database client (e.g., DBeaver, DataGrip) to connect to `localhost:3306`.
  - **Database:** `devex`
  - **User:** `root`
  - **Password:** `123456`
  - You can now see the `users` table and the initialized data.
- **Common Docker Commands:**
  - **Stop all services:** `docker compose down` (Stops and removes all containers with one command)
  - **Start only the database:** `docker compose up -d mysql-db`
  - **View logs:** `docker compose logs -f live-platform-app`

### Troubleshooting

**1. Error: `Error starting userland proxy: listen tcp 0.0.0.0:3306: bind: address already in use`**

- **Cause:** Port `3306` (or `8080`/`8081`/`8082`) is already in use by another process on your local machine.
- **Solution:**
  - Find and stop the process using that port (e.g., you might be running another MySQL instance locally).
  - OR, modify the `docker-compose.yml` file to change the `ports` mapping to another port. For example, change `"3306:3306"` to `"3307:3306"`.

**2. Error: `docker compose build` fails**

- **Cause:** The Docker daemon might not be running, or the Maven build failed.
- **Solution:**
  - Ensure Docker Desktop is running.
  - Try running `mvn clean package` locally before the Docker Compose command to ensure all Java modules compile successfully.
  - Try running `docker compose build --no-cache` to force a rebuild.

**3. Error: `Container ... is unhealthy`**

- **Cause:** The database or cache failed to start.
- **Solution:**
  - Run `docker compose logs mysql-db` or `docker compose logs redis-cache` to see the specific error logs.
  - The most common causes are Docker volume permission issues or insufficient disk space.