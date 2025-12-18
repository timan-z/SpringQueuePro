📦src
 ┣ 📂main
 ┃ ┣ 📂java
 ┃ ┃ ┗ 📂com
 ┃ ┃ ┃ ┗ 📂springqprobackend
 ┃ ┃ ┃ ┃ ┗ 📂springqpro
 ┃ ┃ ┃ ┃ ┃ ┣ 📂config
 ┃ ┃ ┃ ┃ ┃ ┃ ┣ 📜ExecutorConfig.java
 ┃ ┃ ┃ ┃ ┃ ┃ ┣ 📜GlobalExceptionHandler.java
 ┃ ┃ ┃ ┃ ┃ ┃ ┣ 📜ProcessingMetricsConfig.java
 ┃ ┃ ┃ ┃ ┃ ┃ ┣ 📜QueueProperties.java
 ┃ ┃ ┃ ┃ ┃ ┃ ┣ 📜RedisConfig.java
 ┃ ┃ ┃ ┃ ┃ ┃ ┣ 📜SecurityConfig.java
 ┃ ┃ ┃ ┃ ┃ ┃ ┗ 📜TaskHandlerProperties.java
 ┃ ┃ ┃ ┃ ┃ ┣ 📂controller
 ┃ ┃ ┃ ┃ ┃ ┃ ┣ 📂auth
 ┃ ┃ ┃ ┃ ┃ ┃ ┃ ┗ 📜AuthenticationController.java
 ┃ ┃ ┃ ┃ ┃ ┃ ┣ 📂graphql
 ┃ ┃ ┃ ┃ ┃ ┃ ┃ ┣ 📜GraphiQLRedirectController.java
 ┃ ┃ ┃ ┃ ┃ ┃ ┃ ┗ 📜TaskGraphQLController.java
 ┃ ┃ ┃ ┃ ┃ ┃ ┣ 📂rest
 ┃ ┃ ┃ ┃ ┃ ┃ ┃ ┣ 📜ProcessingEventsController.java
 ┃ ┃ ┃ ┃ ┃ ┃ ┃ ┣ 📜ProducerController.java
 ┃ ┃ ┃ ┃ ┃ ┃ ┃ ┣ 📜SystemHealthController.java
 ┃ ┃ ┃ ┃ ┃ ┃ ┃ ┗ 📜TaskRestController.java
 ┃ ┃ ┃ ┃ ┃ ┃ ┗ 📜controllerRecords.java
 ┃ ┃ ┃ ┃ ┃ ┣ 📂domain
 ┃ ┃ ┃ ┃ ┃ ┃ ┣ 📂entity
 ┃ ┃ ┃ ┃ ┃ ┃ ┃ ┣ 📜TaskEntity.java
 ┃ ┃ ┃ ┃ ┃ ┃ ┃ ┗ 📜UserEntity.java
 ┃ ┃ ┃ ┃ ┃ ┃ ┣ 📂event
 ┃ ┃ ┃ ┃ ┃ ┃ ┃ ┗ 📜TaskCreatedEvent.java
 ┃ ┃ ┃ ┃ ┃ ┃ ┗ 📂exception
 ┃ ┃ ┃ ┃ ┃ ┃ ┃ ┗ 📜TaskProcessingException.java
 ┃ ┃ ┃ ┃ ┃ ┣ 📂enums
 ┃ ┃ ┃ ┃ ┃ ┃ ┣ 📜TaskStatus.java
 ┃ ┃ ┃ ┃ ┃ ┃ ┗ 📜TaskType.java
 ┃ ┃ ┃ ┃ ┃ ┣ 📂handlers
 ┃ ┃ ┃ ┃ ┃ ┃ ┣ 📜DataCleanUpHandler.java
 ┃ ┃ ┃ ┃ ┃ ┃ ┣ 📜DefaultHandler.java
 ┃ ┃ ┃ ┃ ┃ ┃ ┣ 📜EmailHandler.java
 ┃ ┃ ┃ ┃ ┃ ┃ ┣ 📜FailAbsHandler.java
 ┃ ┃ ┃ ┃ ┃ ┃ ┣ 📜FailHandler.java
 ┃ ┃ ┃ ┃ ┃ ┃ ┣ 📜NewsLetterHandler.java
 ┃ ┃ ┃ ┃ ┃ ┃ ┣ 📜ReportHandler.java
 ┃ ┃ ┃ ┃ ┃ ┃ ┣ 📜SmsHandler.java
 ┃ ┃ ┃ ┃ ┃ ┃ ┣ 📜TakesLongHandler.java
 ┃ ┃ ┃ ┃ ┃ ┃ ┗ 📜TaskHandler.java
 ┃ ┃ ┃ ┃ ┃ ┣ 📂listeners
 ┃ ┃ ┃ ┃ ┃ ┃ ┗ 📜TaskCreatedListener.java
 ┃ ┃ ┃ ┃ ┃ ┣ 📂mapper
 ┃ ┃ ┃ ┃ ┃ ┃ ┗ 📜TaskMapper.java
 ┃ ┃ ┃ ┃ ┃ ┣ 📂models
 ┃ ┃ ┃ ┃ ┃ ┃ ┣ 📜Task.java
 ┃ ┃ ┃ ┃ ┃ ┃ ┗ 📜TaskHandlerRegistry.java
 ┃ ┃ ┃ ┃ ┃ ┣ 📂redis
 ┃ ┃ ┃ ┃ ┃ ┃ ┣ 📜RedisDistributedLock.java
 ┃ ┃ ┃ ┃ ┃ ┃ ┣ 📜RedisTokenStore.java
 ┃ ┃ ┃ ┃ ┃ ┃ ┣ 📜Redis_Lua_Note.md
 ┃ ┃ ┃ ┃ ┃ ┃ ┗ 📜TaskRedisRepository.java
 ┃ ┃ ┃ ┃ ┃ ┣ 📂repository
 ┃ ┃ ┃ ┃ ┃ ┃ ┣ 📜TaskRepository.java
 ┃ ┃ ┃ ┃ ┃ ┃ ┗ 📜UserRepository.java
 ┃ ┃ ┃ ┃ ┃ ┣ 📂runtime
 ┃ ┃ ┃ ┃ ┃ ┃ ┗ 📜Worker.java
 ┃ ┃ ┃ ┃ ┃ ┣ 📂security
 ┃ ┃ ┃ ┃ ┃ ┃ ┣ 📂dto
 ┃ ┃ ┃ ┃ ┃ ┃ ┃ ┣ 📜AuthRequest.java
 ┃ ┃ ┃ ┃ ┃ ┃ ┃ ┣ 📜AuthResponse.java
 ┃ ┃ ┃ ┃ ┃ ┃ ┃ ┣ 📜LoginRequest.java
 ┃ ┃ ┃ ┃ ┃ ┃ ┃ ┣ 📜RefreshRequest.java
 ┃ ┃ ┃ ┃ ┃ ┃ ┃ ┗ 📜RegisterRequest.java
 ┃ ┃ ┃ ┃ ┃ ┃ ┣ 📜CustomUserDetailsService.java
 ┃ ┃ ┃ ┃ ┃ ┃ ┣ 📜JwtAuthenticationFilter.java
 ┃ ┃ ┃ ┃ ┃ ┃ ┣ 📜JwtUtil.java
 ┃ ┃ ┃ ┃ ┃ ┃ ┗ 📜RefreshTokenService.java
 ┃ ┃ ┃ ┃ ┃ ┣ 📂service
 ┃ ┃ ┃ ┃ ┃ ┃ ┣ 📜ProcessingService.java
 ┃ ┃ ┃ ┃ ┃ ┃ ┣ 📜QueueService.java
 ┃ ┃ ┃ ┃ ┃ ┃ ┗ 📜TaskService.java
 ┃ ┃ ┃ ┃ ┃ ┣ 📂util
 ┃ ┃ ┃ ┃ ┃ ┃ ┣ 📜RealSleeper.java
 ┃ ┃ ┃ ┃ ┃ ┃ ┗ 📜Sleeper.java
 ┃ ┃ ┃ ┃ ┃ ┗ 📜SpringQueueProApplication.java
 ┃ ┗ 📂resources
 ┃ ┃ ┣ 📂graphql
 ┃ ┃ ┃ ┗ 📜schema.graphqls
 ┃ ┃ ┣ 📂static
 ┃ ┃ ┃ ┗ 📂graphiql
 ┃ ┃ ┃ ┃ ┗ 📜index.html
 ┃ ┃ ┣ 📂templates
 ┃ ┃ ┣ 📜application-prod.yml
 ┃ ┃ ┣ 📜application.properties
 ┃ ┃ ┗ 📜application.yml
 ┗ 📂test
 ┃ ┣ 📂java
 ┃ ┃ ┗ 📂com
 ┃ ┃ ┃ ┗ 📂springqprobackend
 ┃ ┃ ┃ ┃ ┗ 📂springqpro
 ┃ ┃ ┃ ┃ ┃ ┣ 📂config
 ┃ ┃ ┃ ┃ ┃ ┃ ┗ 📜RedisTestConfig.java
 ┃ ┃ ┃ ┃ ┃ ┣ 📂handlers
 ┃ ┃ ┃ ┃ ┃ ┃ ┣ 📜DefaultHandlerTests.java
 ┃ ┃ ┃ ┃ ┃ ┃ ┗ 📜FailHandlerTests.java
 ┃ ┃ ┃ ┃ ┃ ┣ 📂integration
 ┃ ┃ ┃ ┃ ┃ ┃ ┣ 📜AuthJwtIntegrationTest.java
 ┃ ┃ ┃ ┃ ┃ ┃ ┣ 📜CreateAndProcessTaskIntegrationTest.java
 ┃ ┃ ┃ ┃ ┃ ┃ ┣ 📜OwnershipGraphQLIntegrationTest.java
 ┃ ┃ ┃ ┃ ┃ ┃ ┣ 📜ProcessingConcurrencyIntegrationTest.java
 ┃ ┃ ┃ ┃ ┃ ┃ ┣ 📜RedisDistributedLockIntegrationTest.java
 ┃ ┃ ┃ ┃ ┃ ┃ ┣ 📜RedisPingIntegrationTest.java
 ┃ ┃ ┃ ┃ ┃ ┃ ┣ 📜RetryBehaviorIntegrationTest.java
 ┃ ┃ ┃ ┃ ┃ ┃ ┣ 📜TaskCacheIntegrationTest.java
 ┃ ┃ ┃ ┃ ┃ ┃ ┗ 📜TaskGraphQLIntegrationTest.java
 ┃ ┃ ┃ ┃ ┃ ┣ 📂models
 ┃ ┃ ┃ ┃ ┃ ┃ ┗ 📜TaskHandlerRegistryTests.java
 ┃ ┃ ┃ ┃ ┃ ┣ 📂runtime
 ┃ ┃ ┃ ┃ ┃ ┃ ┗ 📜WorkerTests.java
 ┃ ┃ ┃ ┃ ┃ ┣ 📂service
 ┃ ┃ ┃ ┃ ┃ ┃ ┗ 📜QueueServiceTests.java
 ┃ ┃ ┃ ┃ ┃ ┣ 📂testcontainers
 ┃ ┃ ┃ ┃ ┃ ┃ ┣ 📜BasePostgresContainer.java
 ┃ ┃ ┃ ┃ ┃ ┃ ┣ 📜BaseRedisContainer.java
 ┃ ┃ ┃ ┃ ┃ ┃ ┣ 📜IntegrationTestBase.java
 ┃ ┃ ┃ ┃ ┃ ┃ ┗ 📜RedisIntegrationTestBase.java
 ┃ ┃ ┃ ┃ ┃ ┗ 📜SpringQueueProApplicationTests.java
 ┃ ┗ 📂resources
 ┃ ┃ ┣ 📜application-test.properties
 ┃ ┃ ┗ 📜application-test.yml