# Market Page Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a read-only BTC market page showing live price, candlesticks, volume, and technical indicators, plus the minimum Docker Compose skeleton needed to serve it.

**Architecture:** A Spring Boot backend fetches OHLCV from Binance's public REST API, computes all indicators in a dependency-free `indicator` package, and serves two REST endpoints — a fast `ticker` and a slower `chart`. A React SPA polls both and renders four vertically stacked lightweight-charts panes sharing one time axis.

**Tech Stack:** Java 21, Spring Boot 3.3.5 (Maven), Spring `RestClient`, Caffeine, JUnit 5 + AssertJ + MockWebServer, React 18 + TypeScript + Vite, lightweight-charts v5, Docker Compose.

## Global Constraints

Copied verbatim from `docs/superpowers/specs/2026-08-17-market-page-design.md`. Every task's requirements implicitly include this section.

- **Safety red line:** The Binance client calls **public market-data endpoints only**. It carries **no API key and performs no request signing**. Never add credentials, signing, or any order-placement endpoint.
- Backend root package: `com.happytrade.market`.
- The `indicator` package has **no Spring dependencies** — pure static functions taking `double[]` and returning `Double[]`.
- Every indicator array has length **exactly equal** to the candle array; index `i` corresponds to `candles[i]`; undefined positions are `null`. The frontend applies no offset.
- `WARM_UP = 200`.
- `Candle.time` is Unix **seconds**.
- Indicator periods are fixed: SMA200, EMA15/30/45/60, RSI14, MACD(12,26,9).
- `limit` range is 50–800; default 500. `interval` is one of `1m`, `5m`, `15m`, `1h`, `4h`, `1d`; default `1h`. `symbol` default `BTCUSDT`.
- Cache TTLs: chart 15s, ticker 3s.
- Binance connect and read timeouts: 5 seconds each.
- The backend declares **no JPA or datasource dependency** in this slice.
- Language policy: all code, comments, docs, and commit messages in **English**.

## File Structure

```
/
  docker-compose.yml                Task 13
  CHANGELOG.md                      Task 14 (modify)
  docs/adr/0002-market-data-and-charting-stack.md   Task 14
  docs/adr/README.md                Task 14 (modify — index row)

  backend/
    Dockerfile                      Task 1
    pom.xml                         Task 1
    src/main/java/com/happytrade/
      HappyTradeApplication.java    Task 1
      market/
        model/Candle.java           Task 2
        model/Ticker.java           Task 2
        model/Interval.java         Task 2
        indicator/Sma.java          Task 3
        indicator/Ema.java          Task 3
        indicator/Rsi.java          Task 4
        indicator/Macd.java         Task 4
        indicator/MacdResult.java   Task 4
        provider/MarketDataProvider.java          Task 5
        provider/BinanceMarketDataProvider.java   Task 5
        provider/UpstreamException.java           Task 5
        service/MarketChartService.java           Task 6
        web/MarketController.java                 Task 7
        web/ChartResponse.java                    Task 7
        web/ApiError.java                         Task 7
        web/MarketExceptionHandler.java           Task 7
        config/RestClientConfig.java              Task 5
        config/CacheConfig.java                   Task 8
    src/main/resources/application.yml            Task 1
    src/test/java/com/happytrade/market/...       per task

  frontend/
    Dockerfile                      Task 9
    package.json                    Task 9
    vite.config.ts                  Task 9
    index.html                      Task 9
    src/main.tsx                    Task 9
    src/App.tsx                     Task 9
    src/index.css                   Task 9
    src/features/market/
      types.ts                      Task 10
      api/marketApi.ts              Task 10
      hooks/usePolling.ts           Task 11
      hooks/useTicker.ts            Task 11
      hooks/useChartData.ts         Task 11
      market.css                    Task 12
      components/PriceHeader.tsx    Task 12
      components/IntervalSelector.tsx  Task 12
      components/IndicatorToggles.tsx  Task 12
      components/PriceChart.tsx     Task 12
      MarketPage.tsx                Task 12
```

---

### Task 1: Backend skeleton that starts and answers a health check

**Files:**
- Create: `backend/pom.xml`
- Create: `backend/src/main/java/com/happytrade/HappyTradeApplication.java`
- Create: `backend/src/main/resources/application.yml`
- Create: `backend/Dockerfile`
- Create: `.gitignore`
- Test: `backend/src/test/java/com/happytrade/HappyTradeApplicationTests.java`

**Interfaces:**
- Consumes: nothing (first task).
- Produces: a runnable Spring Boot app on port 8080 with `spring-boot-starter-web`, `spring-boot-starter-cache`, `caffeine`, `spring-boot-starter-test`, and `mockwebserver` on the test classpath. All later backend tasks build on this Maven module.

- [ ] **Step 1: Confirm you are on the feature branch**

The `feat/market-page` branch already exists and holds this plan. Confirm you are on it rather than on `main`.

Run: `git branch --show-current`
Expected: `feat/market-page`. If not, run `git checkout feat/market-page`.

- [ ] **Step 2: Create `.gitignore`**

```gitignore
# Java
target/
*.class

# Node
node_modules/
dist/

# IDE
.idea/
*.iml
.vscode/

# OS
.DS_Store
Thumbs.db
```

- [ ] **Step 3: Create `backend/pom.xml`**

Note there is deliberately **no** `spring-boot-starter-data-jpa` and **no** datasource driver. Adding them now would force datasource config that nothing uses, and the app would fail to start whenever the `db` container is slow to become healthy.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.3.5</version>
    <relativePath/>
  </parent>

  <groupId>com.happytrade</groupId>
  <artifactId>happy-trade-backend</artifactId>
  <version>0.1.0-SNAPSHOT</version>
  <name>happy-trade-backend</name>

  <properties>
    <java.version>21</java.version>
  </properties>

  <dependencies>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-cache</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    <dependency>
      <groupId>com.github.ben-manes.caffeine</groupId>
      <artifactId>caffeine</artifactId>
    </dependency>

    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>com.squareup.okhttp3</groupId>
      <artifactId>mockwebserver</artifactId>
      <version>4.12.0</version>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
      </plugin>
    </plugins>
  </build>
</project>
```

- [ ] **Step 4: Create the application entry point**

```java
package com.happytrade;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class HappyTradeApplication {

    public static void main(String[] args) {
        SpringApplication.run(HappyTradeApplication.class, args);
    }
}
```

- [ ] **Step 5: Create `backend/src/main/resources/application.yml`**

```yaml
server:
  port: 8080

spring:
  application:
    name: happy-trade-backend

happytrade:
  binance:
    base-url: https://api.binance.com
    timeout-seconds: 5

logging:
  level:
    com.happytrade: INFO
```

- [ ] **Step 6: Write the context-loads test**

```java
package com.happytrade;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class HappyTradeApplicationTests {

    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 7: Generate the Maven wrapper**

Every later task invokes `./mvnw`, so generate the wrapper now. This requires a system Maven once; afterwards the wrapper is self-contained.

Run: `cd backend && mvn -N wrapper:wrapper`
Expected: creates `backend/mvnw`, `backend/mvnw.cmd`, and `backend/.mvn/wrapper/`.

- [ ] **Step 8: Run the test**

Run: `cd backend && ./mvnw test`
Expected: PASS — `Tests run: 1, Failures: 0, Errors: 0`

- [ ] **Step 9: Create `backend/Dockerfile`**

```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/target/happy-trade-backend-0.1.0-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

- [ ] **Step 10: Commit**

```bash
git add .gitignore backend/
git commit -m "feat(backend): add Spring Boot skeleton with health check"
```

---

### Task 2: Domain model

**Files:**
- Create: `backend/src/main/java/com/happytrade/market/model/Candle.java`
- Create: `backend/src/main/java/com/happytrade/market/model/Ticker.java`
- Create: `backend/src/main/java/com/happytrade/market/model/Interval.java`
- Test: `backend/src/test/java/com/happytrade/market/model/IntervalTest.java`

**Interfaces:**
- Consumes: the Maven module from Task 1.
- Produces:
  - `record Candle(long time, double open, double high, double low, double close, double volume)`
  - `record Ticker(String symbol, double price, double changePercent24h, double high24h, double low24h, double volume24h, Instant timestamp)`
  - `enum Interval` with `String code()` and `static Interval fromCode(String code)` throwing `IllegalArgumentException` on unknown codes.

- [ ] **Step 1: Write the failing test**

`Interval` is the only piece here with behaviour, so it is the only piece with a test. `fromCode` is what the controller uses to validate the query parameter, so its rejection path matters.

```java
package com.happytrade.market.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IntervalTest {

    @Test
    void fromCodeResolvesEverySupportedInterval() {
        assertThat(Interval.fromCode("1m")).isEqualTo(Interval.ONE_MINUTE);
        assertThat(Interval.fromCode("5m")).isEqualTo(Interval.FIVE_MINUTES);
        assertThat(Interval.fromCode("15m")).isEqualTo(Interval.FIFTEEN_MINUTES);
        assertThat(Interval.fromCode("1h")).isEqualTo(Interval.ONE_HOUR);
        assertThat(Interval.fromCode("4h")).isEqualTo(Interval.FOUR_HOURS);
        assertThat(Interval.fromCode("1d")).isEqualTo(Interval.ONE_DAY);
    }

    @Test
    void codeRoundTripsBackToTheEnum() {
        for (Interval interval : Interval.values()) {
            assertThat(Interval.fromCode(interval.code())).isEqualTo(interval);
        }
    }

    @Test
    void fromCodeRejectsUnsupportedInterval() {
        assertThatThrownBy(() -> Interval.fromCode("3m"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("3m");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=IntervalTest`
Expected: FAIL — compilation error, `Interval` does not exist.

- [ ] **Step 3: Write the model classes**

`backend/src/main/java/com/happytrade/market/model/Candle.java`:

```java
package com.happytrade.market.model;

/**
 * A single OHLCV candle.
 *
 * <p>{@code time} is the candle open time in Unix <b>seconds</b>, which is the native time format
 * of lightweight-charts. Converting at this boundary means the frontend performs no time
 * arithmetic of its own.
 */
public record Candle(
        long time,
        double open,
        double high,
        double low,
        double close,
        double volume
) {
}
```

`backend/src/main/java/com/happytrade/market/model/Ticker.java`:

```java
package com.happytrade.market.model;

import java.time.Instant;

/**
 * Latest price plus rolling 24-hour statistics.
 *
 * <p>{@code timestamp} is the server's observation time — the moment the backend received the
 * upstream response — not a value supplied by the exchange. The frontend uses it to show how
 * stale the displayed price is while polling is backing off after an error.
 */
public record Ticker(
        String symbol,
        double price,
        double changePercent24h,
        double high24h,
        double low24h,
        double volume24h,
        Instant timestamp
) {
}
```

`backend/src/main/java/com/happytrade/market/model/Interval.java`:

```java
package com.happytrade.market.model;

/** Candle intervals supported by this slice, with their Binance wire codes. */
public enum Interval {

    ONE_MINUTE("1m"),
    FIVE_MINUTES("5m"),
    FIFTEEN_MINUTES("15m"),
    ONE_HOUR("1h"),
    FOUR_HOURS("4h"),
    ONE_DAY("1d");

    private final String code;

    Interval(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static Interval fromCode(String code) {
        for (Interval interval : values()) {
            if (interval.code.equals(code)) {
                return interval;
            }
        }
        throw new IllegalArgumentException("Unsupported interval: " + code);
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=IntervalTest`
Expected: PASS — `Tests run: 3, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/happytrade/market/model backend/src/test/java/com/happytrade/market/model
git commit -m "feat(backend): add Candle, Ticker, and Interval domain model"
```

---

### Task 3: SMA and EMA

**Files:**
- Create: `backend/src/main/java/com/happytrade/market/indicator/Sma.java`
- Create: `backend/src/main/java/com/happytrade/market/indicator/Ema.java`
- Test: `backend/src/test/java/com/happytrade/market/indicator/SmaTest.java`
- Test: `backend/src/test/java/com/happytrade/market/indicator/EmaTest.java`

**Interfaces:**
- Consumes: nothing — this package intentionally has no dependencies, not even on `model`.
- Produces:
  - `Double[] Sma.calculate(double[] values, int period)`
  - `Double[] Ema.calculate(double[] values, int period)`

  Both return an array the same length as `values`, with `null` at every index where the indicator is undefined. Tasks 4 and 6 call these.

- [ ] **Step 1: Write the failing SMA test**

Expected values are hand-computed so a wrong implementation cannot quietly agree with a wrong test. With `period = 3` over `[1,2,3,4,5]`: index 2 is `(1+2+3)/3 = 2.0`, index 3 is `(2+3+4)/3 = 3.0`, index 4 is `(3+4+5)/3 = 4.0`.

```java
package com.happytrade.market.indicator;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SmaTest {

    private static final double TOLERANCE = 1e-9;

    @Test
    void computesTrailingMeanAndNullsTheWarmUpPositions() {
        double[] values = {1, 2, 3, 4, 5};

        Double[] result = Sma.calculate(values, 3);

        assertThat(result).hasSize(5);
        assertThat(result[0]).isNull();
        assertThat(result[1]).isNull();
        assertThat(result[2]).isCloseTo(2.0, org.assertj.core.data.Offset.offset(TOLERANCE));
        assertThat(result[3]).isCloseTo(3.0, org.assertj.core.data.Offset.offset(TOLERANCE));
        assertThat(result[4]).isCloseTo(4.0, org.assertj.core.data.Offset.offset(TOLERANCE));
    }

    @Test
    void producesFirstValueExactlyAtIndexPeriodMinusOne() {
        double[] values = {10, 20, 30};

        Double[] result = Sma.calculate(values, 3);

        assertThat(result[1]).isNull();
        assertThat(result[2]).isCloseTo(20.0, org.assertj.core.data.Offset.offset(TOLERANCE));
    }

    @Test
    void returnsAllNullWhenThereIsLessDataThanThePeriod() {
        double[] values = {1, 2};

        Double[] result = Sma.calculate(values, 5);

        assertThat(result).hasSize(2).containsOnlyNulls();
    }

    @Test
    void rejectsNonPositivePeriod() {
        assertThatThrownBy(() -> Sma.calculate(new double[]{1, 2, 3}, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=SmaTest`
Expected: FAIL — compilation error, `Sma` does not exist.

- [ ] **Step 3: Implement SMA**

```java
package com.happytrade.market.indicator;

/**
 * Simple moving average.
 *
 * <p>Pure function with no framework dependencies so it can be unit-tested directly and reused by
 * the future AI-signal engine unchanged.
 */
public final class Sma {

    private Sma() {
    }

    /**
     * @return an array the same length as {@code values}; {@code null} at every index below
     *         {@code period - 1}, where the average is undefined.
     */
    public static Double[] calculate(double[] values, int period) {
        if (period <= 0) {
            throw new IllegalArgumentException("period must be positive, got " + period);
        }

        Double[] result = new Double[values.length];
        double window = 0;

        for (int i = 0; i < values.length; i++) {
            window += values[i];
            if (i >= period) {
                window -= values[i - period];
            }
            if (i >= period - 1) {
                result[i] = window / period;
            }
        }

        return result;
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=SmaTest`
Expected: PASS — `Tests run: 4, Failures: 0, Errors: 0`

- [ ] **Step 5: Write the failing EMA test**

Hand-computed for `period = 3` over `[1,2,3,4,5]`. `k = 2/(3+1) = 0.5`. Seed at index 2 is `SMA(3) = 2.0`. Index 3: `4*0.5 + 2.0*0.5 = 3.0`. Index 4: `5*0.5 + 3.0*0.5 = 4.0`.

```java
package com.happytrade.market.indicator;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmaTest {

    private static final Offset<Double> TOLERANCE = Offset.offset(1e-9);

    @Test
    void seedsWithSimpleMovingAverageThenSmooths() {
        double[] values = {1, 2, 3, 4, 5};

        Double[] result = Ema.calculate(values, 3);

        assertThat(result).hasSize(5);
        assertThat(result[0]).isNull();
        assertThat(result[1]).isNull();
        assertThat(result[2]).isCloseTo(2.0, TOLERANCE);
        assertThat(result[3]).isCloseTo(3.0, TOLERANCE);
        assertThat(result[4]).isCloseTo(4.0, TOLERANCE);
    }

    @Test
    void producesExactlyOneValueWhenDataLengthEqualsPeriod() {
        double[] values = {10, 20, 30};

        Double[] result = Ema.calculate(values, 3);

        assertThat(result[0]).isNull();
        assertThat(result[1]).isNull();
        assertThat(result[2]).isCloseTo(20.0, TOLERANCE);
    }

    @Test
    void returnsAllNullWhenThereIsLessDataThanThePeriod() {
        double[] values = {1, 2};

        Double[] result = Ema.calculate(values, 5);

        assertThat(result).hasSize(2).containsOnlyNulls();
    }

    @Test
    void rejectsNonPositivePeriod() {
        assertThatThrownBy(() -> Ema.calculate(new double[]{1, 2, 3}, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 6: Run it to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=EmaTest`
Expected: FAIL — compilation error, `Ema` does not exist.

- [ ] **Step 7: Implement EMA**

```java
package com.happytrade.market.indicator;

/**
 * Exponential moving average.
 *
 * <p>Seeded with the simple moving average of the first {@code period} values rather than with the
 * first value alone. Seeding from a single value makes early output depend on how much history
 * happened to be fetched, which would make the same candle produce different numbers on different
 * requests.
 */
public final class Ema {

    private Ema() {
    }

    /**
     * @return an array the same length as {@code values}; {@code null} at every index below
     *         {@code period - 1}, where the average is undefined.
     */
    public static Double[] calculate(double[] values, int period) {
        if (period <= 0) {
            throw new IllegalArgumentException("period must be positive, got " + period);
        }

        Double[] result = new Double[values.length];
        if (values.length < period) {
            return result;
        }

        double seed = 0;
        for (int i = 0; i < period; i++) {
            seed += values[i];
        }

        double multiplier = 2.0 / (period + 1);
        double previous = seed / period;
        result[period - 1] = previous;

        for (int i = period; i < values.length; i++) {
            previous = values[i] * multiplier + previous * (1 - multiplier);
            result[i] = previous;
        }

        return result;
    }
}
```

- [ ] **Step 8: Run it to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=EmaTest`
Expected: PASS — `Tests run: 4, Failures: 0, Errors: 0`

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/com/happytrade/market/indicator backend/src/test/java/com/happytrade/market/indicator
git commit -m "feat(backend): add SMA and EMA indicators"
```

---

### Task 4: RSI and MACD

**Files:**
- Create: `backend/src/main/java/com/happytrade/market/indicator/Rsi.java`
- Create: `backend/src/main/java/com/happytrade/market/indicator/MacdResult.java`
- Create: `backend/src/main/java/com/happytrade/market/indicator/Macd.java`
- Test: `backend/src/test/java/com/happytrade/market/indicator/RsiTest.java`
- Test: `backend/src/test/java/com/happytrade/market/indicator/MacdTest.java`

**Interfaces:**
- Consumes: `Ema.calculate(double[], int)` from Task 3.
- Produces:
  - `Double[] Rsi.calculate(double[] closes, int period)`
  - `record MacdResult(Double[] macd, Double[] signal, Double[] histogram)`
  - `MacdResult Macd.calculate(double[] closes, int fastPeriod, int slowPeriod, int signalPeriod)`

  Task 6 calls both.

- [ ] **Step 1: Write the failing RSI test**

The strictly-rising case is the sharpest available check: with no losses at all, `avgLoss` is 0 and RSI must be exactly 100 rather than dividing by zero. The mirrored strictly-falling case must be exactly 0.

```java
package com.happytrade.market.indicator;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RsiTest {

    private static final Offset<Double> TOLERANCE = Offset.offset(1e-9);

    @Test
    void firstValueLandsExactlyAtIndexPeriod() {
        double[] closes = new double[20];
        for (int i = 0; i < closes.length; i++) {
            closes[i] = 100 + i;
        }

        Double[] result = Rsi.calculate(closes, 14);

        assertThat(result).hasSize(20);
        assertThat(result[13]).isNull();
        assertThat(result[14]).isNotNull();
    }

    @Test
    void strictlyRisingSeriesGivesOneHundred() {
        double[] closes = new double[20];
        for (int i = 0; i < closes.length; i++) {
            closes[i] = 100 + i;
        }

        Double[] result = Rsi.calculate(closes, 14);

        assertThat(result[14]).isCloseTo(100.0, TOLERANCE);
        assertThat(result[19]).isCloseTo(100.0, TOLERANCE);
    }

    @Test
    void strictlyFallingSeriesGivesZero() {
        double[] closes = new double[20];
        for (int i = 0; i < closes.length; i++) {
            closes[i] = 100 - i;
        }

        Double[] result = Rsi.calculate(closes, 14);

        assertThat(result[14]).isCloseTo(0.0, TOLERANCE);
        assertThat(result[19]).isCloseTo(0.0, TOLERANCE);
    }

    @Test
    void alternatingEqualMovesGiveFifty() {
        // +1, -1 repeating produces equal average gain and loss, so RS is 1 and RSI is 50.
        double[] closes = new double[41];
        closes[0] = 100;
        for (int i = 1; i < closes.length; i++) {
            closes[i] = (i % 2 == 1) ? 101 : 100;
        }

        Double[] result = Rsi.calculate(closes, 14);

        assertThat(result[40]).isCloseTo(50.0, Offset.offset(1e-6));
    }

    @Test
    void returnsAllNullWhenDataIsNotLongerThanThePeriod() {
        double[] closes = new double[14];
        for (int i = 0; i < closes.length; i++) {
            closes[i] = 100 + i;
        }

        Double[] result = Rsi.calculate(closes, 14);

        assertThat(result).hasSize(14).containsOnlyNulls();
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=RsiTest`
Expected: FAIL — compilation error, `Rsi` does not exist.

- [ ] **Step 3: Implement RSI**

```java
package com.happytrade.market.indicator;

/**
 * Relative Strength Index using Wilder's smoothing.
 *
 * <p>The first average gain and average loss are the simple means of the first {@code period}
 * gains and losses, so the first defined value lands at index {@code period}. Each average is then
 * smoothed against its own series.
 */
public final class Rsi {

    private Rsi() {
    }

    /**
     * @return an array the same length as {@code closes}; {@code null} at every index below
     *         {@code period}, where the index is undefined.
     */
    public static Double[] calculate(double[] closes, int period) {
        if (period <= 0) {
            throw new IllegalArgumentException("period must be positive, got " + period);
        }

        Double[] result = new Double[closes.length];
        if (closes.length <= period) {
            return result;
        }

        double gainSum = 0;
        double lossSum = 0;
        for (int i = 1; i <= period; i++) {
            double change = closes[i] - closes[i - 1];
            gainSum += Math.max(0, change);
            lossSum += Math.max(0, -change);
        }

        double averageGain = gainSum / period;
        double averageLoss = lossSum / period;
        result[period] = fromAverages(averageGain, averageLoss);

        for (int i = period + 1; i < closes.length; i++) {
            double change = closes[i] - closes[i - 1];
            averageGain = (averageGain * (period - 1) + Math.max(0, change)) / period;
            averageLoss = (averageLoss * (period - 1) + Math.max(0, -change)) / period;
            result[i] = fromAverages(averageGain, averageLoss);
        }

        return result;
    }

    private static double fromAverages(double averageGain, double averageLoss) {
        if (averageLoss == 0) {
            return 100.0;
        }
        double relativeStrength = averageGain / averageLoss;
        return 100 - 100 / (1 + relativeStrength);
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=RsiTest`
Expected: PASS — `Tests run: 5, Failures: 0, Errors: 0`

- [ ] **Step 5: Write the failing MACD test**

The index-mapping assertion is the important one here. The signal line is computed over the MACD series with its `null` warm-up removed, so its results must be mapped back onto the original candle indices. If that mapping is skipped, the whole signal line shifts left and the chart shows crossovers that never happened — and it still looks entirely plausible.

With `fast = 12, slow = 26, signal = 9`: MACD is defined from index 25 onward, so the compacted series starts at index 25 and its 9-element seed makes the first signal value land at compacted index 8, which maps back to original index 33.

```java
package com.happytrade.market.indicator;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MacdTest {

    private static final Offset<Double> TOLERANCE = Offset.offset(1e-9);

    private static double[] risingCloses(int length) {
        double[] closes = new double[length];
        for (int i = 0; i < length; i++) {
            closes[i] = 100 + i;
        }
        return closes;
    }

    @Test
    void allThreeSeriesMatchTheInputLength() {
        double[] closes = risingCloses(60);

        MacdResult result = Macd.calculate(closes, 12, 26, 9);

        assertThat(result.macd()).hasSize(60);
        assertThat(result.signal()).hasSize(60);
        assertThat(result.histogram()).hasSize(60);
    }

    @Test
    void macdIsUndefinedUntilTheSlowEmaExists() {
        double[] closes = risingCloses(60);

        MacdResult result = Macd.calculate(closes, 12, 26, 9);

        assertThat(result.macd()[24]).isNull();
        assertThat(result.macd()[25]).isNotNull();
    }

    @Test
    void signalIsMappedBackOntoOriginalCandleIndices() {
        double[] closes = risingCloses(60);

        MacdResult result = Macd.calculate(closes, 12, 26, 9);

        // MACD starts at index 25; the 9-period signal seed consumes 9 MACD values,
        // so the first signal value belongs at index 25 + 9 - 1 = 33.
        assertThat(result.signal()[32]).isNull();
        assertThat(result.signal()[33]).isNotNull();
    }

    @Test
    void histogramIsMacdMinusSignalWhereverBothExist() {
        double[] closes = risingCloses(60);

        MacdResult result = Macd.calculate(closes, 12, 26, 9);

        for (int i = 0; i < closes.length; i++) {
            if (result.macd()[i] != null && result.signal()[i] != null) {
                assertThat(result.histogram()[i])
                        .isCloseTo(result.macd()[i] - result.signal()[i], TOLERANCE);
            } else {
                assertThat(result.histogram()[i]).isNull();
            }
        }
    }

    @Test
    void returnsAllNullSeriesWhenThereIsNotEnoughData() {
        double[] closes = risingCloses(10);

        MacdResult result = Macd.calculate(closes, 12, 26, 9);

        assertThat(result.macd()).hasSize(10).containsOnlyNulls();
        assertThat(result.signal()).hasSize(10).containsOnlyNulls();
        assertThat(result.histogram()).hasSize(10).containsOnlyNulls();
    }
}
```

- [ ] **Step 6: Run it to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=MacdTest`
Expected: FAIL — compilation error, `Macd` and `MacdResult` do not exist.

- [ ] **Step 7: Implement `MacdResult` and `Macd`**

`backend/src/main/java/com/happytrade/market/indicator/MacdResult.java`:

```java
package com.happytrade.market.indicator;

/**
 * The three MACD series. All three have the same length as the input closes, with {@code null}
 * wherever the value is undefined.
 */
public record MacdResult(Double[] macd, Double[] signal, Double[] histogram) {
}
```

`backend/src/main/java/com/happytrade/market/indicator/Macd.java`:

```java
package com.happytrade.market.indicator;

import java.util.ArrayList;
import java.util.List;

/** Moving Average Convergence Divergence. */
public final class Macd {

    private Macd() {
    }

    public static MacdResult calculate(double[] closes, int fastPeriod, int slowPeriod, int signalPeriod) {
        Double[] fastEma = Ema.calculate(closes, fastPeriod);
        Double[] slowEma = Ema.calculate(closes, slowPeriod);

        Double[] macd = new Double[closes.length];
        for (int i = 0; i < closes.length; i++) {
            if (fastEma[i] != null && slowEma[i] != null) {
                macd[i] = fastEma[i] - slowEma[i];
            }
        }

        Double[] signal = signalMappedToOriginalIndices(macd, signalPeriod);

        Double[] histogram = new Double[closes.length];
        for (int i = 0; i < closes.length; i++) {
            if (macd[i] != null && signal[i] != null) {
                histogram[i] = macd[i] - signal[i];
            }
        }

        return new MacdResult(macd, signal, histogram);
    }

    /**
     * The signal line is an EMA of the MACD series. The MACD series has a {@code null} warm-up
     * prefix, so it is compacted before smoothing and the results are then written back to the
     * indices they came from. Skipping that write-back would shift the signal line left and
     * produce crossovers that never occurred.
     */
    private static Double[] signalMappedToOriginalIndices(Double[] macd, int signalPeriod) {
        List<Integer> sourceIndices = new ArrayList<>();
        List<Double> definedValues = new ArrayList<>();

        for (int i = 0; i < macd.length; i++) {
            if (macd[i] != null) {
                sourceIndices.add(i);
                definedValues.add(macd[i]);
            }
        }

        double[] compacted = new double[definedValues.size()];
        for (int i = 0; i < definedValues.size(); i++) {
            compacted[i] = definedValues.get(i);
        }

        Double[] compactedSignal = Ema.calculate(compacted, signalPeriod);

        Double[] signal = new Double[macd.length];
        for (int i = 0; i < compactedSignal.length; i++) {
            if (compactedSignal[i] != null) {
                signal[sourceIndices.get(i)] = compactedSignal[i];
            }
        }

        return signal;
    }
}
```

- [ ] **Step 8: Run it to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=MacdTest`
Expected: PASS — `Tests run: 5, Failures: 0, Errors: 0`

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/com/happytrade/market/indicator backend/src/test/java/com/happytrade/market/indicator
git commit -m "feat(backend): add RSI and MACD indicators"
```

---

### Task 5: Binance provider

**Files:**
- Create: `backend/src/main/java/com/happytrade/market/provider/MarketDataProvider.java`
- Create: `backend/src/main/java/com/happytrade/market/provider/UpstreamException.java`
- Create: `backend/src/main/java/com/happytrade/market/provider/BinanceMarketDataProvider.java`
- Create: `backend/src/main/java/com/happytrade/market/config/RestClientConfig.java`
- Test: `backend/src/test/java/com/happytrade/market/provider/BinanceMarketDataProviderTest.java`

**Interfaces:**
- Consumes: `Candle`, `Ticker`, `Interval` from Task 2.
- Produces:
  - `interface MarketDataProvider { List<Candle> fetchCandles(String symbol, Interval interval, int limit); Ticker fetchTicker(String symbol); }`
  - `UpstreamException` with nested `RateLimited(int retryAfterSeconds)`, `Blocked`, and `Timeout` subclasses.
  - `BinanceMarketDataProvider` as a `@Component` implementing `MarketDataProvider`, constructed with `(RestClient restClient)`.
  - A `RestClient` bean named `binanceRestClient`.

  Task 6 consumes `MarketDataProvider`; Task 7 maps `UpstreamException` to HTTP statuses.

- [ ] **Step 1: Write the failing provider test**

MockWebServer replays captured Binance payloads, so parsing and error mapping are verified without touching the network — the tests stay deterministic and run offline.

```java
package com.happytrade.market.provider;

import com.happytrade.market.model.Candle;
import com.happytrade.market.model.Interval;
import com.happytrade.market.model.Ticker;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BinanceMarketDataProviderTest {

    private MockWebServer server;
    private BinanceMarketDataProvider provider;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        RestClient restClient = RestClient.builder()
                .baseUrl(server.url("/").toString())
                .build();
        provider = new BinanceMarketDataProvider(restClient);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void parsesKlinesIntoCandlesWithUnixSecondTimes() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        [
                          [1755440000000,"63980.10","64420.00","63910.50","64312.50","812.34",
                           1755443599999,"52000000.00",1200,"400.00","25000000.00","0"],
                          [1755443600000,"64312.50","64500.00","64200.00","64450.00","645.10",
                           1755447199999,"41000000.00",980,"320.00","20000000.00","0"]
                        ]
                        """));

        List<Candle> candles = provider.fetchCandles("BTCUSDT", Interval.ONE_HOUR, 700);

        assertThat(candles).hasSize(2);
        Candle first = candles.get(0);
        assertThat(first.time()).isEqualTo(1755440000L);
        assertThat(first.open()).isEqualTo(63980.10);
        assertThat(first.high()).isEqualTo(64420.00);
        assertThat(first.low()).isEqualTo(63910.50);
        assertThat(first.close()).isEqualTo(64312.50);
        assertThat(first.volume()).isEqualTo(812.34);

        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath())
                .contains("/api/v3/klines")
                .contains("symbol=BTCUSDT")
                .contains("interval=1h")
                .contains("limit=700");
    }

    @Test
    void sendsNoApiKeyOrSignature() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("[]"));

        provider.fetchCandles("BTCUSDT", Interval.ONE_HOUR, 100);

        RecordedRequest request = server.takeRequest();
        assertThat(request.getHeader("X-MBX-APIKEY")).isNull();
        assertThat(request.getPath()).doesNotContain("signature");
    }

    @Test
    void parsesTicker() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "symbol": "BTCUSDT",
                          "lastPrice": "64312.50",
                          "priceChangePercent": "2.41",
                          "highPrice": "65100.00",
                          "lowPrice": "62800.00",
                          "volume": "41235.60"
                        }
                        """));

        Ticker ticker = provider.fetchTicker("BTCUSDT");

        assertThat(ticker.symbol()).isEqualTo("BTCUSDT");
        assertThat(ticker.price()).isEqualTo(64312.50);
        assertThat(ticker.changePercent24h()).isEqualTo(2.41);
        assertThat(ticker.high24h()).isEqualTo(65100.00);
        assertThat(ticker.low24h()).isEqualTo(62800.00);
        assertThat(ticker.volume24h()).isEqualTo(41235.60);
        assertThat(ticker.timestamp()).isNotNull();
    }

    @Test
    void mapsRateLimitToRateLimitedWithRetryAfter() {
        server.enqueue(new MockResponse().setResponseCode(429).setHeader("Retry-After", "30"));

        assertThatThrownBy(() -> provider.fetchTicker("BTCUSDT"))
                .isInstanceOf(UpstreamException.RateLimited.class)
                .satisfies(thrown ->
                        assertThat(((UpstreamException.RateLimited) thrown).retryAfterSeconds()).isEqualTo(30));
    }

    @Test
    void mapsTeapotToRateLimited() {
        server.enqueue(new MockResponse().setResponseCode(418));

        assertThatThrownBy(() -> provider.fetchTicker("BTCUSDT"))
                .isInstanceOf(UpstreamException.RateLimited.class);
    }

    @Test
    void mapsUnavailableForLegalReasonsToBlocked() {
        server.enqueue(new MockResponse().setResponseCode(451));

        assertThatThrownBy(() -> provider.fetchTicker("BTCUSDT"))
                .isInstanceOf(UpstreamException.Blocked.class);
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=BinanceMarketDataProviderTest`
Expected: FAIL — compilation error, `BinanceMarketDataProvider` does not exist.

- [ ] **Step 3: Create the provider interface**

```java
package com.happytrade.market.provider;

import com.happytrade.market.model.Candle;
import com.happytrade.market.model.Interval;
import com.happytrade.market.model.Ticker;

import java.util.List;

/**
 * Read-only market data access.
 *
 * <p>This interface deliberately exposes no order-placement operation. The project's hard rule is
 * that nothing may place orders automatically.
 */
public interface MarketDataProvider {

    List<Candle> fetchCandles(String symbol, Interval interval, int limit);

    Ticker fetchTicker(String symbol);
}
```

- [ ] **Step 4: Create the upstream exception hierarchy**

```java
package com.happytrade.market.provider;

/** Failures originating from the upstream market data source. */
public abstract class UpstreamException extends RuntimeException {

    protected UpstreamException(String message) {
        super(message);
    }

    protected UpstreamException(String message, Throwable cause) {
        super(message, cause);
    }

    /** The upstream rejected us for sending too many requests. */
    public static class RateLimited extends UpstreamException {

        private final int retryAfterSeconds;

        public RateLimited(int retryAfterSeconds) {
            super("Upstream rate limited the request; retry after " + retryAfterSeconds + "s");
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public int retryAfterSeconds() {
            return retryAfterSeconds;
        }
    }

    /** The upstream refuses to serve this region. Retrying will not help. */
    public static class Blocked extends UpstreamException {

        public Blocked() {
            super("Upstream is not available from this region or network");
        }
    }

    /** The upstream did not respond within the configured timeout. */
    public static class Timeout extends UpstreamException {

        public Timeout(Throwable cause) {
            super("Upstream did not respond in time", cause);
        }
    }
}
```

- [ ] **Step 5: Create the `RestClient` bean**

```java
package com.happytrade.market.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient binanceRestClient(
            @Value("${happytrade.binance.base-url}") String baseUrl,
            @Value("${happytrade.binance.timeout-seconds}") long timeoutSeconds) {

        Duration timeout = Duration.ofSeconds(timeoutSeconds);
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(timeout)
                .withReadTimeout(timeout);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(ClientHttpRequestFactories.get(settings))
                .build();
    }
}
```

- [ ] **Step 6: Implement the Binance provider**

```java
package com.happytrade.market.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.happytrade.market.model.Candle;
import com.happytrade.market.model.Interval;
import com.happytrade.market.model.Ticker;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Reads public market data from Binance.
 *
 * <p><b>Safety:</b> this client calls public endpoints only. It sends no API key and performs no
 * request signing, so it is structurally incapable of placing an order. Adding credentials here
 * would be a red-line change requiring its own ADR.
 */
@Component
public class BinanceMarketDataProvider implements MarketDataProvider {

    private static final int DEFAULT_RETRY_AFTER_SECONDS = 60;

    private final RestClient restClient;

    public BinanceMarketDataProvider(RestClient binanceRestClient) {
        this.restClient = binanceRestClient;
    }

    @Override
    public List<Candle> fetchCandles(String symbol, Interval interval, int limit) {
        JsonNode body = get(uriBuilder -> uriBuilder
                .path("/api/v3/klines")
                .queryParam("symbol", symbol)
                .queryParam("interval", interval.code())
                .queryParam("limit", limit)
                .build());

        List<Candle> candles = new ArrayList<>();
        for (JsonNode row : body) {
            candles.add(new Candle(
                    row.get(0).asLong() / 1000,
                    row.get(1).asDouble(),
                    row.get(2).asDouble(),
                    row.get(3).asDouble(),
                    row.get(4).asDouble(),
                    row.get(5).asDouble()
            ));
        }
        return candles;
    }

    @Override
    public Ticker fetchTicker(String symbol) {
        JsonNode body = get(uriBuilder -> uriBuilder
                .path("/api/v3/ticker/24hr")
                .queryParam("symbol", symbol)
                .build());

        return new Ticker(
                body.get("symbol").asText(),
                body.get("lastPrice").asDouble(),
                body.get("priceChangePercent").asDouble(),
                body.get("highPrice").asDouble(),
                body.get("lowPrice").asDouble(),
                body.get("volume").asDouble(),
                Instant.now()
        );
    }

    private JsonNode get(Function<UriBuilder, URI> uriFunction) {
        try {
            return restClient.get()
                    .uri(uriFunction)
                    .retrieve()
                    .onStatus(status -> status.value() == 429 || status.value() == 418,
                            (request, response) -> {
                                throw new UpstreamException.RateLimited(
                                        retryAfterSeconds(response.getHeaders().getFirst("Retry-After")));
                            })
                    .onStatus(status -> status.value() == 451,
                            (request, response) -> {
                                throw new UpstreamException.Blocked();
                            })
                    .body(JsonNode.class);
        } catch (ResourceAccessException e) {
            throw new UpstreamException.Timeout(e);
        }
    }

    private static int retryAfterSeconds(String headerValue) {
        if (headerValue == null) {
            return DEFAULT_RETRY_AFTER_SECONDS;
        }
        try {
            return Integer.parseInt(headerValue.trim());
        } catch (NumberFormatException e) {
            return DEFAULT_RETRY_AFTER_SECONDS;
        }
    }
}
```

- [ ] **Step 7: Run the test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=BinanceMarketDataProviderTest`
Expected: PASS — `Tests run: 6, Failures: 0, Errors: 0`

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/happytrade/market/provider backend/src/main/java/com/happytrade/market/config backend/src/test/java/com/happytrade/market/provider
git commit -m "feat(backend): add keyless Binance market data provider"
```

---

### Task 6: Chart service with warm-up and alignment

**Files:**
- Create: `backend/src/main/java/com/happytrade/market/service/MarketChartService.java`
- Test: `backend/src/test/java/com/happytrade/market/service/MarketChartServiceTest.java`

**Interfaces:**
- Consumes: `MarketDataProvider` (Task 5), `Sma`/`Ema` (Task 3), `Rsi`/`Macd`/`MacdResult` (Task 4), `Candle`/`Interval`/`Ticker` (Task 2).
- Produces:
  - `MarketChartService` as a `@Service` constructed with `(MarketDataProvider provider)`.
  - `record ChartData(String symbol, String interval, List<Candle> candles, IndicatorSeries indicators)` — nested inside `MarketChartService`.
  - `record IndicatorSeries(Double[] sma200, Double[] ema15, Double[] ema30, Double[] ema45, Double[] ema60, Double[] rsi14, Double[] macd, Double[] macdSignal, Double[] macdHistogram)` — nested inside `MarketChartService`.
  - `ChartData buildChart(String symbol, Interval interval, int limit)`
  - `Ticker fetchTicker(String symbol)`
  - `public static final int WARM_UP = 200`

  Task 7 turns `ChartData` into the JSON response; Task 8 wraps both methods in caches.

- [ ] **Step 1: Write the failing service test**

A hand-written stub provider is used rather than a mocking framework: the test needs to control exactly how many candles come back, and asserting on the returned array lengths is the whole point.

```java
package com.happytrade.market.service;

import com.happytrade.market.model.Candle;
import com.happytrade.market.model.Interval;
import com.happytrade.market.model.Ticker;
import com.happytrade.market.provider.MarketDataProvider;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarketChartServiceTest {

    /** Returns exactly {@code available} synthetic candles regardless of what was requested. */
    private static class StubProvider implements MarketDataProvider {

        private final int available;
        int lastRequestedLimit;

        StubProvider(int available) {
            this.available = available;
        }

        @Override
        public List<Candle> fetchCandles(String symbol, Interval interval, int limit) {
            lastRequestedLimit = limit;
            List<Candle> candles = new ArrayList<>();
            for (int i = 0; i < available; i++) {
                double close = 100 + i;
                candles.add(new Candle(1_700_000_000L + i * 3600L, close, close + 1, close - 1, close, 10 + i));
            }
            return candles;
        }

        @Override
        public Ticker fetchTicker(String symbol) {
            return new Ticker(symbol, 1, 2, 3, 4, 5, Instant.EPOCH);
        }
    }

    @Test
    void requestsWarmUpCandlesOnTopOfTheDisplayLimit() {
        StubProvider provider = new StubProvider(700);
        MarketChartService service = new MarketChartService(provider);

        service.buildChart("BTCUSDT", Interval.ONE_HOUR, 500);

        assertThat(provider.lastRequestedLimit).isEqualTo(700);
    }

    @Test
    void trimsWarmUpAndReturnsExactlyTheRequestedCandles() {
        MarketChartService service = new MarketChartService(new StubProvider(700));

        MarketChartService.ChartData data = service.buildChart("BTCUSDT", Interval.ONE_HOUR, 500);

        assertThat(data.candles()).hasSize(500);
    }

    @Test
    void everyIndicatorSeriesHasTheSameLengthAsTheCandles() {
        MarketChartService service = new MarketChartService(new StubProvider(700));

        MarketChartService.ChartData data = service.buildChart("BTCUSDT", Interval.ONE_HOUR, 500);
        MarketChartService.IndicatorSeries indicators = data.indicators();
        int expected = data.candles().size();

        assertThat(indicators.sma200()).hasSize(expected);
        assertThat(indicators.ema15()).hasSize(expected);
        assertThat(indicators.ema30()).hasSize(expected);
        assertThat(indicators.ema45()).hasSize(expected);
        assertThat(indicators.ema60()).hasSize(expected);
        assertThat(indicators.rsi14()).hasSize(expected);
        assertThat(indicators.macd()).hasSize(expected);
        assertThat(indicators.macdSignal()).hasSize(expected);
        assertThat(indicators.macdHistogram()).hasSize(expected);
    }

    @Test
    void warmUpMeansSma200IsDefinedAtTheVeryFirstReturnedCandle() {
        MarketChartService service = new MarketChartService(new StubProvider(700));

        MarketChartService.ChartData data = service.buildChart("BTCUSDT", Interval.ONE_HOUR, 500);

        // 200 warm-up candles were dropped, so SMA200 is already defined at index 0.
        assertThat(data.indicators().sma200()[0]).isNotNull();
    }

    @Test
    void keepsEveryCandleWhenUpstreamReturnsFewerThanTheDisplayLimit() {
        MarketChartService service = new MarketChartService(new StubProvider(100));

        MarketChartService.ChartData data = service.buildChart("BTCUSDT", Interval.ONE_HOUR, 500);

        // drop = max(0, min(200, 100 - 500)) = 0 — showing 100 candles with partially null
        // indicators beats showing almost none.
        assertThat(data.candles()).hasSize(100);
        assertThat(data.indicators().sma200()).hasSize(100).containsOnlyNulls();
    }

    @Test
    void echoesSymbolAndIntervalCode() {
        MarketChartService service = new MarketChartService(new StubProvider(700));

        MarketChartService.ChartData data = service.buildChart("BTCUSDT", Interval.FOUR_HOURS, 500);

        assertThat(data.symbol()).isEqualTo("BTCUSDT");
        assertThat(data.interval()).isEqualTo("4h");
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=MarketChartServiceTest`
Expected: FAIL — compilation error, `MarketChartService` does not exist.

- [ ] **Step 3: Implement the service**

```java
package com.happytrade.market.service;

import com.happytrade.market.indicator.Ema;
import com.happytrade.market.indicator.Macd;
import com.happytrade.market.indicator.MacdResult;
import com.happytrade.market.indicator.Rsi;
import com.happytrade.market.indicator.Sma;
import com.happytrade.market.model.Candle;
import com.happytrade.market.model.Interval;
import com.happytrade.market.model.Ticker;
import com.happytrade.market.provider.MarketDataProvider;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * Builds the chart payload: fetch with warm-up, compute indicators over the full window, then trim
 * the warm-up away so the caller sees only the candles it asked for.
 */
@Service
public class MarketChartService {

    /**
     * Extra candles fetched ahead of the display window. Set by SMA200, the longest lookback in
     * use. If an indicator with a longer lookback is added, this must grow and the {@code limit}
     * ceiling must shrink to keep {@code limit + WARM_UP <= 1000} (the Binance per-request cap).
     */
    public static final int WARM_UP = 200;

    private final MarketDataProvider provider;

    public MarketChartService(MarketDataProvider provider) {
        this.provider = provider;
    }

    public record IndicatorSeries(
            Double[] sma200,
            Double[] ema15,
            Double[] ema30,
            Double[] ema45,
            Double[] ema60,
            Double[] rsi14,
            Double[] macd,
            Double[] macdSignal,
            Double[] macdHistogram
    ) {
    }

    public record ChartData(
            String symbol,
            String interval,
            List<Candle> candles,
            IndicatorSeries indicators
    ) {
    }

    public ChartData buildChart(String symbol, Interval interval, int limit) {
        List<Candle> fetched = provider.fetchCandles(symbol, interval, limit + WARM_UP);

        double[] closes = fetched.stream().mapToDouble(Candle::close).toArray();

        Double[] sma200 = Sma.calculate(closes, 200);
        Double[] ema15 = Ema.calculate(closes, 15);
        Double[] ema30 = Ema.calculate(closes, 30);
        Double[] ema45 = Ema.calculate(closes, 45);
        Double[] ema60 = Ema.calculate(closes, 60);
        Double[] rsi14 = Rsi.calculate(closes, 14);
        MacdResult macd = Macd.calculate(closes, 12, 26, 9);

        int drop = Math.max(0, Math.min(WARM_UP, fetched.size() - limit));

        return new ChartData(
                symbol,
                interval.code(),
                List.copyOf(fetched.subList(drop, fetched.size())),
                new IndicatorSeries(
                        trim(sma200, drop),
                        trim(ema15, drop),
                        trim(ema30, drop),
                        trim(ema45, drop),
                        trim(ema60, drop),
                        trim(rsi14, drop),
                        trim(macd.macd(), drop),
                        trim(macd.signal(), drop),
                        trim(macd.histogram(), drop)
                )
        );
    }

    public Ticker fetchTicker(String symbol) {
        return provider.fetchTicker(symbol);
    }

    private static Double[] trim(Double[] series, int drop) {
        return Arrays.copyOfRange(series, drop, series.length);
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=MarketChartServiceTest`
Expected: PASS — `Tests run: 6, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/happytrade/market/service backend/src/test/java/com/happytrade/market/service
git commit -m "feat(backend): add chart service with indicator warm-up and alignment"
```

---

### Task 7: REST endpoints and error mapping

**Files:**
- Create: `backend/src/main/java/com/happytrade/market/web/ChartResponse.java`
- Create: `backend/src/main/java/com/happytrade/market/web/ApiError.java`
- Create: `backend/src/main/java/com/happytrade/market/web/MarketController.java`
- Create: `backend/src/main/java/com/happytrade/market/web/MarketExceptionHandler.java`
- Test: `backend/src/test/java/com/happytrade/market/web/MarketControllerTest.java`

**Interfaces:**
- Consumes: `MarketChartService` with `buildChart` and `fetchTicker` (Task 6), `UpstreamException` (Task 5), `Interval` and `Ticker` (Task 2).
- Produces: `GET /api/market/ticker` and `GET /api/market/chart` with the JSON shape defined in the spec. Task 10 mirrors these types in TypeScript.

- [ ] **Step 1: Write the failing controller test**

```java
package com.happytrade.market.web;

import com.happytrade.market.model.Candle;
import com.happytrade.market.model.Interval;
import com.happytrade.market.model.Ticker;
import com.happytrade.market.provider.UpstreamException;
import com.happytrade.market.service.MarketChartService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MarketController.class)
class MarketControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MarketChartService service;

    private static MarketChartService.ChartData sampleChart() {
        return new MarketChartService.ChartData(
                "BTCUSDT",
                "1h",
                List.of(new Candle(1755440000L, 63980.1, 64420.0, 63910.5, 64312.5, 812.34)),
                new MarketChartService.IndicatorSeries(
                        new Double[]{64010.2},
                        new Double[]{64288.1},
                        new Double[]{64201.7},
                        new Double[]{64150.3},
                        new Double[]{64098.8},
                        new Double[]{61.7},
                        new Double[]{128.4},
                        new Double[]{96.2},
                        new Double[]{32.2}
                )
        );
    }

    @Test
    void returnsTicker() throws Exception {
        given(service.fetchTicker("BTCUSDT")).willReturn(
                new Ticker("BTCUSDT", 64312.50, 2.41, 65100.0, 62800.0, 41235.6,
                        Instant.parse("2026-08-17T14:23:05Z")));

        mockMvc.perform(get("/api/market/ticker").param("symbol", "BTCUSDT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("BTCUSDT"))
                .andExpect(jsonPath("$.price").value(64312.50))
                .andExpect(jsonPath("$.changePercent24h").value(2.41))
                .andExpect(jsonPath("$.high24h").value(65100.0))
                .andExpect(jsonPath("$.low24h").value(62800.0))
                .andExpect(jsonPath("$.volume24h").value(41235.6))
                .andExpect(jsonPath("$.timestamp").value("2026-08-17T14:23:05Z"));
    }

    @Test
    void returnsChartWithNestedIndicatorShape() throws Exception {
        given(service.buildChart(anyString(), any(Interval.class), anyInt())).willReturn(sampleChart());

        mockMvc.perform(get("/api/market/chart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("BTCUSDT"))
                .andExpect(jsonPath("$.interval").value("1h"))
                .andExpect(jsonPath("$.candles[0].time").value(1755440000L))
                .andExpect(jsonPath("$.candles[0].close").value(64312.5))
                .andExpect(jsonPath("$.indicators.sma200[0]").value(64010.2))
                .andExpect(jsonPath("$.indicators.ema15[0]").value(64288.1))
                .andExpect(jsonPath("$.indicators.rsi14[0]").value(61.7))
                .andExpect(jsonPath("$.indicators.macd.macd[0]").value(128.4))
                .andExpect(jsonPath("$.indicators.macd.signal[0]").value(96.2))
                .andExpect(jsonPath("$.indicators.macd.histogram[0]").value(32.2));
    }

    @Test
    void rejectsUnsupportedInterval() throws Exception {
        mockMvc.perform(get("/api/market/chart").param("interval", "3m"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    void rejectsLimitAboveTheCeiling() throws Exception {
        mockMvc.perform(get("/api/market/chart").param("limit", "801"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    void rejectsLimitBelowTheFloor() throws Exception {
        mockMvc.perform(get("/api/market/chart").param("limit", "49"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    void mapsRateLimitedToServiceUnavailableWithRetryAfter() throws Exception {
        given(service.fetchTicker(anyString())).willThrow(new UpstreamException.RateLimited(30));

        mockMvc.perform(get("/api/market/ticker"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("UPSTREAM_RATE_LIMITED"))
                .andExpect(jsonPath("$.retryAfter").value(30));
    }

    @Test
    void mapsBlockedToServiceUnavailable() throws Exception {
        given(service.fetchTicker(anyString())).willThrow(new UpstreamException.Blocked());

        mockMvc.perform(get("/api/market/ticker"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("UPSTREAM_BLOCKED"));
    }

    @Test
    void mapsTimeoutToGatewayTimeout() throws Exception {
        given(service.fetchTicker(anyString()))
                .willThrow(new UpstreamException.Timeout(new RuntimeException("boom")));

        mockMvc.perform(get("/api/market/ticker"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value("UPSTREAM_TIMEOUT"));
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=MarketControllerTest`
Expected: FAIL — compilation error, `MarketController` does not exist.

- [ ] **Step 3: Create the response DTOs**

`backend/src/main/java/com/happytrade/market/web/ChartResponse.java`:

```java
package com.happytrade.market.web;

import com.happytrade.market.model.Candle;
import com.happytrade.market.service.MarketChartService;

import java.util.List;

/**
 * Wire shape of {@code GET /api/market/chart}.
 *
 * <p>MACD is nested one level deeper than the other indicators because its three series belong
 * together; the service keeps them flat internally, and this record does the regrouping.
 */
public record ChartResponse(
        String symbol,
        String interval,
        List<Candle> candles,
        Indicators indicators
) {

    public record MacdSeries(Double[] macd, Double[] signal, Double[] histogram) {
    }

    public record Indicators(
            Double[] sma200,
            Double[] ema15,
            Double[] ema30,
            Double[] ema45,
            Double[] ema60,
            Double[] rsi14,
            MacdSeries macd
    ) {
    }

    public static ChartResponse from(MarketChartService.ChartData data) {
        MarketChartService.IndicatorSeries series = data.indicators();
        return new ChartResponse(
                data.symbol(),
                data.interval(),
                data.candles(),
                new Indicators(
                        series.sma200(),
                        series.ema15(),
                        series.ema30(),
                        series.ema45(),
                        series.ema60(),
                        series.rsi14(),
                        new MacdSeries(series.macd(), series.macdSignal(), series.macdHistogram())
                )
        );
    }
}
```

`backend/src/main/java/com/happytrade/market/web/ApiError.java`:

```java
package com.happytrade.market.web;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Error payload. {@code retryAfter} is present only when the client should try again after a
 * specific number of seconds.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String code, String message, Integer retryAfter) {

    public static ApiError of(String code, String message) {
        return new ApiError(code, message, null);
    }
}
```

- [ ] **Step 4: Create the controller**

```java
package com.happytrade.market.web;

import com.happytrade.market.model.Interval;
import com.happytrade.market.model.Ticker;
import com.happytrade.market.service.MarketChartService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only market data endpoints. No endpoint here places or simulates an order. */
@RestController
@RequestMapping("/api/market")
public class MarketController {

    private static final int MIN_LIMIT = 50;
    private static final int MAX_LIMIT = 800;

    private final MarketChartService service;

    public MarketController(MarketChartService service) {
        this.service = service;
    }

    @GetMapping("/ticker")
    public Ticker ticker(@RequestParam(defaultValue = "BTCUSDT") String symbol) {
        return service.fetchTicker(validateSymbol(symbol));
    }

    @GetMapping("/chart")
    public ChartResponse chart(
            @RequestParam(defaultValue = "BTCUSDT") String symbol,
            @RequestParam(defaultValue = "1h") String interval,
            @RequestParam(defaultValue = "500") int limit) {

        if (limit < MIN_LIMIT || limit > MAX_LIMIT) {
            throw new IllegalArgumentException(
                    "limit must be between " + MIN_LIMIT + " and " + MAX_LIMIT + ", got " + limit);
        }

        return ChartResponse.from(
                service.buildChart(validateSymbol(symbol), Interval.fromCode(interval), limit));
    }

    private static String validateSymbol(String symbol) {
        if (!symbol.matches("[A-Z0-9]{1,20}")) {
            throw new IllegalArgumentException("Invalid symbol: " + symbol);
        }
        return symbol;
    }
}
```

- [ ] **Step 5: Create the exception handler**

```java
package com.happytrade.market.web;

import com.happytrade.market.provider.UpstreamException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class MarketExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleInvalidParameter(IllegalArgumentException e) {
        return ResponseEntity.badRequest()
                .body(ApiError.of("INVALID_PARAMETER", e.getMessage()));
    }

    @ExceptionHandler(UpstreamException.RateLimited.class)
    public ResponseEntity<ApiError> handleRateLimited(UpstreamException.RateLimited e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError("UPSTREAM_RATE_LIMITED", e.getMessage(), e.retryAfterSeconds()));
    }

    @ExceptionHandler(UpstreamException.Blocked.class)
    public ResponseEntity<ApiError> handleBlocked(UpstreamException.Blocked e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiError.of("UPSTREAM_BLOCKED", e.getMessage()));
    }

    @ExceptionHandler(UpstreamException.Timeout.class)
    public ResponseEntity<ApiError> handleTimeout(UpstreamException.Timeout e) {
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                .body(ApiError.of("UPSTREAM_TIMEOUT", e.getMessage()));
    }
}
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=MarketControllerTest`
Expected: PASS — `Tests run: 8, Failures: 0, Errors: 0`

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/happytrade/market/web backend/src/test/java/com/happytrade/market/web
git commit -m "feat(backend): add market REST endpoints with upstream error mapping"
```

---

### Task 8: Caching

**Files:**
- Create: `backend/src/main/java/com/happytrade/market/config/CacheConfig.java`
- Modify: `backend/src/main/java/com/happytrade/market/service/MarketChartService.java` (add `@Cacheable` to both public methods)
- Test: `backend/src/test/java/com/happytrade/market/service/MarketChartServiceCachingTest.java`

**Interfaces:**
- Consumes: `MarketChartService` (Task 6).
- Produces: cache names `CacheConfig.CHART_CACHE = "marketChart"` and `CacheConfig.TICKER_CACHE = "marketTicker"`.

- [ ] **Step 1: Write the failing caching test**

Two browser tabs polling at once must not double the upstream calls. This test asserts exactly that: two identical requests, one upstream call.

```java
package com.happytrade.market.service;

import com.happytrade.market.model.Candle;
import com.happytrade.market.model.Interval;
import com.happytrade.market.model.Ticker;
import com.happytrade.market.provider.MarketDataProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class MarketChartServiceCachingTest {

    static final AtomicInteger candleCalls = new AtomicInteger();
    static final AtomicInteger tickerCalls = new AtomicInteger();

    @TestConfiguration
    static class CountingProviderConfig {

        @Bean
        @Primary
        MarketDataProvider countingProvider() {
            return new MarketDataProvider() {
                @Override
                public List<Candle> fetchCandles(String symbol, Interval interval, int limit) {
                    candleCalls.incrementAndGet();
                    List<Candle> candles = new ArrayList<>();
                    for (int i = 0; i < 700; i++) {
                        candles.add(new Candle(1_700_000_000L + i * 3600L, 100, 101, 99, 100 + i, 10));
                    }
                    return candles;
                }

                @Override
                public Ticker fetchTicker(String symbol) {
                    tickerCalls.incrementAndGet();
                    return new Ticker(symbol, 1, 2, 3, 4, 5, Instant.EPOCH);
                }
            };
        }
    }

    @Autowired
    private MarketChartService service;

    @Test
    void identicalChartRequestsHitTheUpstreamOnlyOnce() {
        candleCalls.set(0);

        service.buildChart("BTCUSDT", Interval.ONE_HOUR, 500);
        service.buildChart("BTCUSDT", Interval.ONE_HOUR, 500);

        assertThat(candleCalls.get()).isEqualTo(1);
    }

    @Test
    void differentIntervalsAreCachedSeparately() {
        candleCalls.set(0);

        service.buildChart("ETHUSDT", Interval.ONE_MINUTE, 500);
        service.buildChart("ETHUSDT", Interval.FOUR_HOURS, 500);

        assertThat(candleCalls.get()).isEqualTo(2);
    }

    @Test
    void identicalTickerRequestsHitTheUpstreamOnlyOnce() {
        tickerCalls.set(0);

        service.fetchTicker("XRPUSDT");
        service.fetchTicker("XRPUSDT");

        assertThat(tickerCalls.get()).isEqualTo(1);
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=MarketChartServiceCachingTest`
Expected: FAIL — `expected 1 but was 2`, because no caching exists yet.

- [ ] **Step 3: Create the cache configuration**

Two caches with different TTLs, so a single `spring.cache.caffeine.spec` property will not do. `SimpleCacheManager` holding two independently configured `CaffeineCache` instances is the straightforward way to express that.

```java
package com.happytrade.market.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String CHART_CACHE = "marketChart";
    public static final String TICKER_CACHE = "marketTicker";

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(
                // 15s: several browser tabs polling at once must not multiply upstream calls.
                new CaffeineCache(CHART_CACHE, Caffeine.newBuilder()
                        .expireAfterWrite(Duration.ofSeconds(15))
                        .maximumSize(200)
                        .build()),
                // 3s: below the frontend's 5s poll, so a refresh nearly always sees fresh data.
                new CaffeineCache(TICKER_CACHE, Caffeine.newBuilder()
                        .expireAfterWrite(Duration.ofSeconds(3))
                        .maximumSize(50)
                        .build())
        ));
        return manager;
    }
}
```

- [ ] **Step 4: Annotate the service methods**

In `MarketChartService.java`, add these imports:

```java
import com.happytrade.market.config.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
```

Then annotate the two public methods. Replace:

```java
    public ChartData buildChart(String symbol, Interval interval, int limit) {
```

with:

```java
    @Cacheable(cacheNames = CacheConfig.CHART_CACHE, key = "#symbol + ':' + #interval + ':' + #limit")
    public ChartData buildChart(String symbol, Interval interval, int limit) {
```

And replace:

```java
    public Ticker fetchTicker(String symbol) {
```

with:

```java
    @Cacheable(cacheNames = CacheConfig.TICKER_CACHE, key = "#symbol")
    public Ticker fetchTicker(String symbol) {
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=MarketChartServiceCachingTest`
Expected: PASS — `Tests run: 3, Failures: 0, Errors: 0`

- [ ] **Step 6: Run the whole backend suite**

Run: `cd backend && ./mvnw test`
Expected: PASS — all tests from Tasks 1–8 green.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/happytrade/market/config/CacheConfig.java backend/src/main/java/com/happytrade/market/service
git commit -m "feat(backend): cache chart and ticker responses with Caffeine"
```

---

### Task 9: Frontend scaffold

**Files:**
- Create: `frontend/package.json`
- Create: `frontend/tsconfig.json`
- Create: `frontend/tsconfig.node.json`
- Create: `frontend/vite.config.ts`
- Create: `frontend/index.html`
- Create: `frontend/src/main.tsx`
- Create: `frontend/src/App.tsx`
- Create: `frontend/src/index.css`
- Create: `frontend/Dockerfile`

**Interfaces:**
- Consumes: the backend endpoints from Task 7 (via the dev-server proxy).
- Produces: a Vite dev server on port 5173 that proxies `/api` to the backend, with `lightweight-charts` and `vitest` installed. All later frontend tasks build on this.

- [ ] **Step 1: Create `frontend/package.json`**

```json
{
  "name": "happy-trade-frontend",
  "private": true,
  "version": "0.1.0",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "tsc -b && vite build",
    "preview": "vite preview",
    "test": "vitest run"
  },
  "dependencies": {
    "lightweight-charts": "^5.0.0",
    "react": "^18.3.1",
    "react-dom": "^18.3.1"
  },
  "devDependencies": {
    "@types/react": "^18.3.12",
    "@types/react-dom": "^18.3.1",
    "@vitejs/plugin-react": "^4.3.3",
    "typescript": "^5.6.3",
    "vite": "^5.4.10",
    "vitest": "^2.1.4"
  }
}
```

- [ ] **Step 2: Create the TypeScript configs**

`frontend/tsconfig.json`:

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "useDefineForClassFields": true,
    "lib": ["ES2022", "DOM", "DOM.Iterable"],
    "module": "ESNext",
    "skipLibCheck": true,
    "moduleResolution": "bundler",
    "allowImportingTsExtensions": true,
    "resolveJsonModule": true,
    "isolatedModules": true,
    "noEmit": true,
    "jsx": "react-jsx",
    "strict": true,
    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "noFallthroughCasesInSwitch": true
  },
  "include": ["src"],
  "references": [{ "path": "./tsconfig.node.json" }]
}
```

`frontend/tsconfig.node.json`:

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "lib": ["ES2023"],
    "module": "ESNext",
    "skipLibCheck": true,
    "moduleResolution": "bundler",
    "allowSyntheticDefaultImports": true,
    "strict": true,
    "noEmit": true
  },
  "include": ["vite.config.ts"]
}
```

- [ ] **Step 3: Create `frontend/vite.config.ts`**

The proxy target is an environment variable because the host differs between local development (`localhost`) and Docker Compose (the `backend` service name).

```ts
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

const proxyTarget = process.env.VITE_PROXY_TARGET ?? 'http://localhost:8080';

export default defineConfig({
  plugins: [react()],
  server: {
    host: '0.0.0.0',
    port: 5173,
    proxy: {
      '/api': {
        target: proxyTarget,
        changeOrigin: true,
      },
    },
  },
});
```

- [ ] **Step 4: Create `frontend/index.html`**

```html
<!doctype html>
<html lang="zh-Hant">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>Happy Trade</title>
  </head>
  <body>
    <div id="root"></div>
    <script type="module" src="/src/main.tsx"></script>
  </body>
</html>
```

- [ ] **Step 5: Create the entry point, root component, and stylesheet**

`frontend/src/main.tsx`:

```tsx
import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import './index.css';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
```

`frontend/src/App.tsx` — a placeholder replaced in Task 12:

```tsx
export default function App() {
  return <div>Happy Trade</div>;
}
```

`frontend/src/index.css`:

```css
:root {
  --bg: #0d1117;
  --panel: #161b22;
  --border: #30363d;
  --text: #e6edf3;
  --muted: #8b949e;
  --up: #26a69a;
  --down: #ef5350;

  color-scheme: dark;
}

* {
  box-sizing: border-box;
}

html,
body,
#root {
  height: 100%;
  margin: 0;
}

body {
  background: var(--bg);
  color: var(--text);
  font-family: system-ui, -apple-system, 'Segoe UI', 'Noto Sans TC', sans-serif;
}
```

- [ ] **Step 6: Install and verify the dev server starts**

Run: `cd frontend && npm install && npm run build`
Expected: `npm install` completes, then `vite build` succeeds and writes `dist/`.

- [ ] **Step 7: Create `frontend/Dockerfile`**

This runs the Vite dev server rather than a production build, matching the spec's `frontend` service description.

```dockerfile
FROM node:22-alpine
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
EXPOSE 5173
CMD ["npm", "run", "dev", "--", "--host", "0.0.0.0"]
```

- [ ] **Step 8: Commit**

```bash
git add frontend/
git commit -m "feat(frontend): scaffold Vite React TypeScript app with API proxy"
```

---

### Task 10: Frontend types and API client

**Files:**
- Create: `frontend/src/features/market/types.ts`
- Create: `frontend/src/features/market/api/marketApi.ts`
- Test: `frontend/src/features/market/api/marketApi.test.ts`

**Interfaces:**
- Consumes: the JSON contract from Task 7.
- Produces:
  - Types `Candle`, `TickerData`, `MacdSeries`, `Indicators`, `ChartData`, `IntervalCode`, `ApiErrorBody`.
  - `class MarketApiError extends Error` with `code: string` and `retryAfter?: number`.
  - `fetchTicker(symbol?: string): Promise<TickerData>`
  - `fetchChart(symbol: string, interval: IntervalCode, limit?: number): Promise<ChartData>`
  - `const INTERVALS: readonly IntervalCode[]`

  Tasks 11 and 12 consume all of these.

- [ ] **Step 1: Write the failing API test**

```ts
import { afterEach, describe, expect, it, vi } from 'vitest';
import { fetchChart, fetchTicker, MarketApiError } from './marketApi';

function mockFetchOnce(body: unknown, init: { status?: number } = {}) {
  const response = new Response(JSON.stringify(body), {
    status: init.status ?? 200,
    headers: { 'Content-Type': 'application/json' },
  });
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response));
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('fetchTicker', () => {
  it('requests the ticker endpoint with the symbol', async () => {
    mockFetchOnce({
      symbol: 'BTCUSDT',
      price: 64312.5,
      changePercent24h: 2.41,
      high24h: 65100,
      low24h: 62800,
      volume24h: 41235.6,
      timestamp: '2026-08-17T14:23:05Z',
    });

    const ticker = await fetchTicker('BTCUSDT');

    expect(ticker.price).toBe(64312.5);
    expect(fetch).toHaveBeenCalledWith('/api/market/ticker?symbol=BTCUSDT');
  });

  it('throws MarketApiError carrying the backend code and retryAfter', async () => {
    mockFetchOnce(
      { code: 'UPSTREAM_RATE_LIMITED', message: 'slow down', retryAfter: 30 },
      { status: 503 },
    );

    await expect(fetchTicker('BTCUSDT')).rejects.toMatchObject({
      code: 'UPSTREAM_RATE_LIMITED',
      retryAfter: 30,
    });
    await expect(fetchTicker('BTCUSDT')).rejects.toBeInstanceOf(MarketApiError);
  });
});

describe('fetchChart', () => {
  it('requests the chart endpoint with symbol, interval, and limit', async () => {
    mockFetchOnce({
      symbol: 'BTCUSDT',
      interval: '1h',
      candles: [],
      indicators: {
        sma200: [],
        ema15: [],
        ema30: [],
        ema45: [],
        ema60: [],
        rsi14: [],
        macd: { macd: [], signal: [], histogram: [] },
      },
    });

    await fetchChart('BTCUSDT', '1h', 500);

    expect(fetch).toHaveBeenCalledWith('/api/market/chart?symbol=BTCUSDT&interval=1h&limit=500');
  });
});
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd frontend && npm test`
Expected: FAIL — cannot resolve `./marketApi`.

- [ ] **Step 3: Create `types.ts`**

```ts
/** Mirrors the backend DTOs in com.happytrade.market.web. */

export const INTERVALS = ['1m', '5m', '15m', '1h', '4h', '1d'] as const;

export type IntervalCode = (typeof INTERVALS)[number];

export interface Candle {
  /** Unix seconds — lightweight-charts' native time format. */
  time: number;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
}

export interface TickerData {
  symbol: string;
  price: number;
  changePercent24h: number;
  high24h: number;
  low24h: number;
  volume24h: number;
  /** Server observation time, ISO-8601. */
  timestamp: string;
}

export interface MacdSeries {
  macd: (number | null)[];
  signal: (number | null)[];
  histogram: (number | null)[];
}

export interface Indicators {
  sma200: (number | null)[];
  ema15: (number | null)[];
  ema30: (number | null)[];
  ema45: (number | null)[];
  ema60: (number | null)[];
  rsi14: (number | null)[];
  macd: MacdSeries;
}

export interface ChartData {
  symbol: string;
  interval: IntervalCode;
  candles: Candle[];
  /**
   * Every array here has exactly the same length as `candles`, and index i corresponds to
   * candles[i]. Apply no offset.
   */
  indicators: Indicators;
}

export interface ApiErrorBody {
  code: string;
  message: string;
  retryAfter?: number;
}
```

- [ ] **Step 4: Create `api/marketApi.ts`**

```ts
import type { ApiErrorBody, ChartData, IntervalCode, TickerData } from '../types';

export class MarketApiError extends Error {
  readonly code: string;
  readonly retryAfter?: number;

  constructor(body: ApiErrorBody) {
    super(body.message);
    this.name = 'MarketApiError';
    this.code = body.code;
    this.retryAfter = body.retryAfter;
  }
}

async function getJson<T>(url: string): Promise<T> {
  const response = await fetch(url);

  if (!response.ok) {
    let body: ApiErrorBody;
    try {
      body = (await response.json()) as ApiErrorBody;
    } catch {
      body = { code: 'UNKNOWN', message: `Request failed with status ${response.status}` };
    }
    throw new MarketApiError(body);
  }

  return (await response.json()) as T;
}

export function fetchTicker(symbol = 'BTCUSDT'): Promise<TickerData> {
  return getJson<TickerData>(`/api/market/ticker?symbol=${symbol}`);
}

export function fetchChart(
  symbol: string,
  interval: IntervalCode,
  limit = 500,
): Promise<ChartData> {
  return getJson<ChartData>(
    `/api/market/chart?symbol=${symbol}&interval=${interval}&limit=${limit}`,
  );
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd frontend && npm test`
Expected: PASS — 3 tests passed.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/features/market
git commit -m "feat(frontend): add market API types and typed client"
```

---

### Task 11: Polling hooks

**Files:**
- Create: `frontend/src/features/market/hooks/usePolling.ts`
- Create: `frontend/src/features/market/hooks/useTicker.ts`
- Create: `frontend/src/features/market/hooks/useChartData.ts`

**Interfaces:**
- Consumes: `fetchTicker`, `fetchChart`, `MarketApiError` (Task 10); types from `types.ts`.
- Produces:
  - `interface PollingState<T> { data: T | null; error: MarketApiError | null; isStale: boolean }`
  - `usePolling<T>(fetcher: () => Promise<T>, intervalMs: number): PollingState<T>`
  - `useTicker(symbol: string): PollingState<TickerData>`
  - `useChartData(symbol: string, interval: IntervalCode): PollingState<ChartData>`
  - `CHART_POLL_MS: Record<IntervalCode, number>`

  Task 12 consumes `useTicker` and `useChartData`.

- [ ] **Step 1: Create `hooks/usePolling.ts`**

Two behaviours matter more than they look. First, a failed poll never clears `data` — while watching a market, a blank screen is much worse than a price that is 30 seconds old. Second, polling stops while the tab is hidden and fires once immediately on return, so a backgrounded tab neither burns upstream quota nor shows a stale price when the user comes back.

```ts
import { useEffect, useRef, useState } from 'react';
import { MarketApiError } from '../api/marketApi';

export interface PollingState<T> {
  data: T | null;
  error: MarketApiError | null;
  /** True when the last attempt failed but earlier data is still on screen. */
  isStale: boolean;
}

export function usePolling<T>(fetcher: () => Promise<T>, intervalMs: number): PollingState<T> {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<MarketApiError | null>(null);

  // Kept in a ref so changing the fetcher identity does not restart the timer.
  const fetcherRef = useRef(fetcher);
  fetcherRef.current = fetcher;

  useEffect(() => {
    let cancelled = false;
    let timer: number | undefined;

    const run = async () => {
      try {
        const result = await fetcherRef.current();
        if (cancelled) return;
        setData(result);
        setError(null);
      } catch (e) {
        if (cancelled) return;
        // Deliberately does not clear `data` — the last good payload stays on screen.
        setError(e instanceof MarketApiError ? e : new MarketApiError({ code: 'NETWORK', message: String(e) }));
      }
    };

    const start = () => {
      void run();
      timer = window.setInterval(run, intervalMs);
    };

    const stop = () => {
      if (timer !== undefined) {
        window.clearInterval(timer);
        timer = undefined;
      }
    };

    const onVisibilityChange = () => {
      stop();
      if (document.visibilityState === 'visible') {
        start();
      }
    };

    if (document.visibilityState === 'visible') {
      start();
    }
    document.addEventListener('visibilitychange', onVisibilityChange);

    return () => {
      cancelled = true;
      stop();
      document.removeEventListener('visibilitychange', onVisibilityChange);
    };
  }, [intervalMs]);

  return { data, error, isStale: error !== null && data !== null };
}
```

- [ ] **Step 2: Create `hooks/useTicker.ts`**

```ts
import { useCallback } from 'react';
import { fetchTicker } from '../api/marketApi';
import type { TickerData } from '../types';
import { usePolling, type PollingState } from './usePolling';

const TICKER_POLL_MS = 5_000;

export function useTicker(symbol: string): PollingState<TickerData> {
  const fetcher = useCallback(() => fetchTicker(symbol), [symbol]);
  return usePolling(fetcher, TICKER_POLL_MS);
}
```

- [ ] **Step 3: Create `hooks/useChartData.ts`**

Poll cadence scales with the candle interval — re-fetching daily candles every five seconds is pointless load.

```ts
import { useCallback } from 'react';
import { fetchChart } from '../api/marketApi';
import type { ChartData, IntervalCode } from '../types';
import { usePolling, type PollingState } from './usePolling';

export const CHART_POLL_MS: Record<IntervalCode, number> = {
  '1m': 20_000,
  '5m': 30_000,
  '15m': 60_000,
  '1h': 60_000,
  '4h': 120_000,
  '1d': 300_000,
};

export function useChartData(symbol: string, interval: IntervalCode): PollingState<ChartData> {
  const fetcher = useCallback(() => fetchChart(symbol, interval), [symbol, interval]);
  return usePolling(fetcher, CHART_POLL_MS[interval]);
}
```

- [ ] **Step 4: Verify it type-checks**

Run: `cd frontend && npx tsc -b`
Expected: no output, exit code 0.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/features/market/hooks
git commit -m "feat(frontend): add visibility-aware polling hooks"
```

---

### Task 12: Market page UI

**Files:**
- Create: `frontend/src/features/market/components/PriceHeader.tsx`
- Create: `frontend/src/features/market/components/IntervalSelector.tsx`
- Create: `frontend/src/features/market/components/IndicatorToggles.tsx`
- Create: `frontend/src/features/market/components/PriceChart.tsx`
- Create: `frontend/src/features/market/MarketPage.tsx`
- Create: `frontend/src/features/market/market.css`
- Modify: `frontend/src/App.tsx`

**Interfaces:**
- Consumes: `useTicker`, `useChartData` (Task 11); `INTERVALS`, `ChartData`, `IntervalCode`, `TickerData` (Task 10).
- Produces:
  - `type IndicatorKey = 'sma200' | 'ema15' | 'ema30' | 'ema45' | 'ema60' | 'rsi14' | 'macd'`
  - `interface IndicatorVisibility extends Record<IndicatorKey, boolean>`
  - Components `PriceHeader`, `IntervalSelector`, `IndicatorToggles`, `PriceChart`, `MarketPage`.

- [ ] **Step 1: Create `market.css`**

```css
.market-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 12px 16px;
  gap: 12px;
}

.price-header {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 16px;
}

.price-header__symbol {
  font-size: 18px;
  font-weight: 600;
}

.price-header__price {
  font-size: 28px;
  font-variant-numeric: tabular-nums;
}

.price-header__change--up {
  color: var(--up);
}

.price-header__change--down {
  color: var(--down);
}

.price-header__stat {
  color: var(--muted);
  font-size: 13px;
}

.price-header__stat span {
  color: var(--text);
  font-variant-numeric: tabular-nums;
}

.badge {
  border-radius: 4px;
  font-size: 12px;
  padding: 2px 8px;
}

.badge--stale {
  background: #4d3800;
  color: #f0c000;
}

.badge--error {
  background: #4d1414;
  color: #ff8080;
}

.control-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
}

.interval-selector {
  display: flex;
  gap: 4px;
}

.interval-selector button {
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: 4px;
  color: var(--muted);
  cursor: pointer;
  font-size: 13px;
  padding: 4px 12px;
}

.interval-selector button[aria-pressed='true'] {
  background: #1f6feb;
  border-color: #1f6feb;
  color: #fff;
}

.indicator-toggles {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 13px;
}

.indicator-toggles label {
  align-items: center;
  cursor: pointer;
  display: flex;
  gap: 4px;
}

.chart-container {
  border: 1px solid var(--border);
  border-radius: 6px;
  flex: 1;
  min-height: 480px;
  overflow: hidden;
}
```

- [ ] **Step 2: Create `PriceHeader.tsx`**

```tsx
import type { TickerData } from '../types';

interface Props {
  ticker: TickerData | null;
  isStale: boolean;
  errorMessage: string | null;
}

function format(value: number, digits = 2): string {
  return value.toLocaleString('en-US', {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  });
}

export function PriceHeader({ ticker, isStale, errorMessage }: Props) {
  if (!ticker) {
    return (
      <div className="price-header">
        <span className="price-header__symbol">BTC/USDT</span>
        <span className="price-header__stat">載入中…</span>
        {errorMessage && <span className="badge badge--error">{errorMessage}</span>}
      </div>
    );
  }

  const up = ticker.changePercent24h >= 0;

  return (
    <div className="price-header">
      <span className="price-header__symbol">BTC/USDT</span>
      <span className="price-header__price">${format(ticker.price)}</span>
      <span className={up ? 'price-header__change--up' : 'price-header__change--down'}>
        {up ? '+' : ''}
        {format(ticker.changePercent24h)}%
      </span>
      <span className="price-header__stat">
        24h 高 <span>{format(ticker.high24h)}</span>
      </span>
      <span className="price-header__stat">
        24h 低 <span>{format(ticker.low24h)}</span>
      </span>
      <span className="price-header__stat">
        24h 量 <span>{format(ticker.volume24h)}</span>
      </span>
      {isStale && <span className="badge badge--stale">資料延遲</span>}
      {errorMessage && <span className="badge badge--error">{errorMessage}</span>}
    </div>
  );
}
```

- [ ] **Step 3: Create `IntervalSelector.tsx`**

```tsx
import { INTERVALS, type IntervalCode } from '../types';

interface Props {
  value: IntervalCode;
  onChange: (interval: IntervalCode) => void;
}

export function IntervalSelector({ value, onChange }: Props) {
  return (
    <div className="interval-selector">
      {INTERVALS.map((interval) => (
        <button
          key={interval}
          type="button"
          aria-pressed={interval === value}
          onClick={() => onChange(interval)}
        >
          {interval}
        </button>
      ))}
    </div>
  );
}
```

- [ ] **Step 4: Create `IndicatorToggles.tsx`**

```tsx
export type IndicatorKey =
  | 'sma200'
  | 'ema15'
  | 'ema30'
  | 'ema45'
  | 'ema60'
  | 'rsi14'
  | 'macd';

export type IndicatorVisibility = Record<IndicatorKey, boolean>;

export const DEFAULT_VISIBILITY: IndicatorVisibility = {
  sma200: true,
  ema15: true,
  ema30: true,
  ema45: true,
  ema60: true,
  rsi14: true,
  macd: true,
};

const LABELS: Record<IndicatorKey, string> = {
  sma200: 'SMA 200',
  ema15: 'EMA 15',
  ema30: 'EMA 30',
  ema45: 'EMA 45',
  ema60: 'EMA 60',
  rsi14: 'RSI 14',
  macd: 'MACD',
};

interface Props {
  value: IndicatorVisibility;
  onChange: (value: IndicatorVisibility) => void;
}

export function IndicatorToggles({ value, onChange }: Props) {
  return (
    <div className="indicator-toggles">
      {(Object.keys(LABELS) as IndicatorKey[]).map((key) => (
        <label key={key}>
          <input
            type="checkbox"
            checked={value[key]}
            onChange={(e) => onChange({ ...value, [key]: e.target.checked })}
          />
          {LABELS[key]}
        </label>
      ))}
    </div>
  );
}
```

- [ ] **Step 5: Create `PriceChart.tsx`**

One chart instance with four panes, which is what gives the shared time axis and a crosshair that moves across all four at once. That shared axis is the whole point of the layout: it makes "price broke out, and here is where RSI and MACD were at that moment" readable in one glance.

If the v5 Panes API turns out to be unworkable, the documented fallback is four separate chart instances with their `timeScale` ranges synchronised through `subscribeVisibleLogicalRangeChange`, guarding against feedback loops between them. Try the panes approach first.

```tsx
import {
  CandlestickSeries,
  createChart,
  HistogramSeries,
  LineSeries,
  type IChartApi,
  type ISeriesApi,
  type UTCTimestamp,
} from 'lightweight-charts';
import { useEffect, useRef } from 'react';
import type { ChartData } from '../types';
import type { IndicatorVisibility } from './IndicatorToggles';

const PANE_PRICE = 0;
const PANE_VOLUME = 1;
const PANE_RSI = 2;
const PANE_MACD = 3;

const EMA_COLORS = {
  ema15: '#f0b429',
  ema30: '#4dabf7',
  ema45: '#b197fc',
  ema60: '#63e6be',
} as const;

/** Drops null gaps — lightweight-charts renders a break wherever a point is absent. */
function toLine(candles: ChartData['candles'], values: (number | null)[]) {
  const points: { time: UTCTimestamp; value: number }[] = [];
  for (let i = 0; i < candles.length; i++) {
    const value = values[i];
    if (value !== null && value !== undefined) {
      points.push({ time: candles[i].time as UTCTimestamp, value });
    }
  }
  return points;
}

interface Props {
  data: ChartData | null;
  visibility: IndicatorVisibility;
}

/**
 * Series handles are held in one named, precisely-typed object rather than a loose record.
 * A `Record<string, ISeriesApi<'Candlestick' | 'Histogram' | 'Line'>>` forces a cast at every
 * `setData` call, and those casts are exactly what would hide a mistake like feeding line points
 * to the candlestick series.
 */
interface ChartSeries {
  price: ISeriesApi<'Candlestick'>;
  volume: ISeriesApi<'Histogram'>;
  sma200: ISeriesApi<'Line'>;
  ema15: ISeriesApi<'Line'>;
  ema30: ISeriesApi<'Line'>;
  ema45: ISeriesApi<'Line'>;
  ema60: ISeriesApi<'Line'>;
  rsi14: ISeriesApi<'Line'>;
  macd: ISeriesApi<'Line'>;
  macdSignal: ISeriesApi<'Line'>;
  macdHistogram: ISeriesApi<'Histogram'>;
}

export function PriceChart({ data, visibility }: Props) {
  const containerRef = useRef<HTMLDivElement>(null);
  const chartRef = useRef<IChartApi | null>(null);
  const seriesRef = useRef<ChartSeries | null>(null);

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;

    const chart = createChart(container, {
      autoSize: true,
      layout: {
        background: { color: '#161b22' },
        textColor: '#8b949e',
        panes: { separatorColor: '#30363d', separatorHoverColor: '#484f58' },
      },
      grid: {
        vertLines: { color: '#21262d' },
        horzLines: { color: '#21262d' },
      },
      timeScale: { timeVisible: true, secondsVisible: false },
    });
    chartRef.current = chart;

    const price = chart.addSeries(
      CandlestickSeries,
      { upColor: '#26a69a', downColor: '#ef5350', borderVisible: false,
        wickUpColor: '#26a69a', wickDownColor: '#ef5350' },
      PANE_PRICE,
    );
    const volume = chart.addSeries(
      HistogramSeries,
      { color: '#4a5568', priceFormat: { type: 'volume' } },
      PANE_VOLUME,
    );
    const sma200 = chart.addSeries(LineSeries, { color: '#ffffff', lineWidth: 2 }, PANE_PRICE);
    const ema15 = chart.addSeries(LineSeries, { color: EMA_COLORS.ema15, lineWidth: 1 }, PANE_PRICE);
    const ema30 = chart.addSeries(LineSeries, { color: EMA_COLORS.ema30, lineWidth: 1 }, PANE_PRICE);
    const ema45 = chart.addSeries(LineSeries, { color: EMA_COLORS.ema45, lineWidth: 1 }, PANE_PRICE);
    const ema60 = chart.addSeries(LineSeries, { color: EMA_COLORS.ema60, lineWidth: 1 }, PANE_PRICE);
    const rsi14 = chart.addSeries(LineSeries, { color: '#d19a66', lineWidth: 1 }, PANE_RSI);
    const macd = chart.addSeries(LineSeries, { color: '#4dabf7', lineWidth: 1 }, PANE_MACD);
    const macdSignal = chart.addSeries(LineSeries, { color: '#f0b429', lineWidth: 1 }, PANE_MACD);
    const macdHistogram = chart.addSeries(HistogramSeries, { color: '#4a5568' }, PANE_MACD);

    rsi14.createPriceLine({ price: 70, color: '#ef5350', lineWidth: 1, title: '70' });
    rsi14.createPriceLine({ price: 30, color: '#26a69a', lineWidth: 1, title: '30' });

    seriesRef.current = {
      price, volume, sma200, ema15, ema30, ema45, ema60, rsi14, macd, macdSignal, macdHistogram,
    };

    // Pane heights follow the 60 / 15 / 12 / 13 split from the spec.
    const total = container.clientHeight;
    const panes = chart.panes();
    panes[PANE_PRICE]?.setHeight(total * 0.6);
    panes[PANE_VOLUME]?.setHeight(total * 0.15);
    panes[PANE_RSI]?.setHeight(total * 0.12);
    panes[PANE_MACD]?.setHeight(total * 0.13);

    return () => {
      chart.remove();
      chartRef.current = null;
      seriesRef.current = null;
    };
  }, []);

  useEffect(() => {
    const s = seriesRef.current;
    if (!data || !s) return;
    const { candles, indicators } = data;

    s.price.setData(
      candles.map((c) => ({
        time: c.time as UTCTimestamp,
        open: c.open,
        high: c.high,
        low: c.low,
        close: c.close,
      })),
    );

    s.volume.setData(
      candles.map((c) => ({
        time: c.time as UTCTimestamp,
        value: c.volume,
        color: c.close >= c.open ? 'rgba(38,166,154,0.5)' : 'rgba(239,83,80,0.5)',
      })),
    );

    s.sma200.setData(toLine(candles, indicators.sma200));
    s.ema15.setData(toLine(candles, indicators.ema15));
    s.ema30.setData(toLine(candles, indicators.ema30));
    s.ema45.setData(toLine(candles, indicators.ema45));
    s.ema60.setData(toLine(candles, indicators.ema60));
    s.rsi14.setData(toLine(candles, indicators.rsi14));
    s.macd.setData(toLine(candles, indicators.macd.macd));
    s.macdSignal.setData(toLine(candles, indicators.macd.signal));

    s.macdHistogram.setData(
      toLine(candles, indicators.macd.histogram).map((point) => ({
        ...point,
        color: point.value >= 0 ? 'rgba(38,166,154,0.6)' : 'rgba(239,83,80,0.6)',
      })),
    );
  }, [data]);

  useEffect(() => {
    const s = seriesRef.current;
    if (!s) return;

    s.sma200.applyOptions({ visible: visibility.sma200 });
    s.ema15.applyOptions({ visible: visibility.ema15 });
    s.ema30.applyOptions({ visible: visibility.ema30 });
    s.ema45.applyOptions({ visible: visibility.ema45 });
    s.ema60.applyOptions({ visible: visibility.ema60 });
    s.rsi14.applyOptions({ visible: visibility.rsi14 });
    s.macd.applyOptions({ visible: visibility.macd });
    s.macdSignal.applyOptions({ visible: visibility.macd });
    s.macdHistogram.applyOptions({ visible: visibility.macd });
  }, [visibility]);

  return <div className="chart-container" ref={containerRef} />;
}
```

- [ ] **Step 6: Create `MarketPage.tsx`**

```tsx
import { useState } from 'react';
import { IndicatorToggles, DEFAULT_VISIBILITY, type IndicatorVisibility } from './components/IndicatorToggles';
import { IntervalSelector } from './components/IntervalSelector';
import { PriceChart } from './components/PriceChart';
import { PriceHeader } from './components/PriceHeader';
import { useChartData } from './hooks/useChartData';
import { useTicker } from './hooks/useTicker';
import type { IntervalCode } from './types';
import './market.css';

const SYMBOL = 'BTCUSDT';

export function MarketPage() {
  // Named `selectedInterval` rather than `interval` so `setInterval` does not shadow the global
  // timer function — a shadow that silently breaks any later code in this file that needs it.
  const [selectedInterval, setSelectedInterval] = useState<IntervalCode>('1h');
  const [visibility, setVisibility] = useState<IndicatorVisibility>(DEFAULT_VISIBILITY);

  const ticker = useTicker(SYMBOL);
  const chart = useChartData(SYMBOL, selectedInterval);

  // UPSTREAM_BLOCKED is terminal — retrying will not help, so it is surfaced as a hard error.
  const blocked =
    ticker.error?.code === 'UPSTREAM_BLOCKED' || chart.error?.code === 'UPSTREAM_BLOCKED';

  return (
    <div className="market-page">
      <PriceHeader
        ticker={ticker.data}
        isStale={ticker.isStale || chart.isStale}
        errorMessage={blocked ? '此網路無法連線交易所' : null}
      />
      <div className="control-row">
        <IntervalSelector value={selectedInterval} onChange={setSelectedInterval} />
        <IndicatorToggles value={visibility} onChange={setVisibility} />
      </div>
      <PriceChart data={chart.data} visibility={visibility} />
    </div>
  );
}
```

- [ ] **Step 7: Replace `frontend/src/App.tsx`**

```tsx
import { MarketPage } from './features/market/MarketPage';

export default function App() {
  return <MarketPage />;
}
```

- [ ] **Step 8: Verify it builds**

Run: `cd frontend && npx tsc -b && npm run build`
Expected: type check clean, `vite build` succeeds.

- [ ] **Step 9: Verify it renders against the live backend**

Run in one terminal: `cd backend && ./mvnw spring-boot:run`
Run in another: `cd frontend && npm run dev`
Then open `http://localhost:5173`.
Expected: four stacked panes render — candlesticks with five overlaid moving averages, a volume histogram, RSI with 30/70 lines, and MACD with its histogram. Moving the crosshair moves it in all four panes at once. Unchecking a toggle hides that series.

- [ ] **Step 10: Commit**

```bash
git add frontend/src
git commit -m "feat(frontend): add BTC market page with four-pane chart"
```

---

### Task 13: Docker Compose

**Files:**
- Create: `docker-compose.yml`

**Interfaces:**
- Consumes: `backend/Dockerfile` (Task 1), `frontend/Dockerfile` (Task 9).
- Produces: `docker compose up` bringing all three services online — success criterion 1 from the spec.

- [ ] **Step 1: Create `docker-compose.yml`**

`db` is started but the backend does not connect to it. It is present so the running stack matches ADR-0001 and so the AI-signal slice can adopt it without re-provisioning.

```yaml
services:
  db:
    image: postgres:16
    environment:
      POSTGRES_USER: happytrade
      POSTGRES_PASSWORD: happytrade
      POSTGRES_DB: happytrade
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U happytrade"]
      interval: 10s
      timeout: 5s
      retries: 5

  backend:
    build: ./backend
    ports:
      - "8080:8080"
    environment:
      HAPPYTRADE_BINANCE_BASE_URL: https://api.binance.com

  frontend:
    build: ./frontend
    ports:
      - "5173:5173"
    environment:
      VITE_PROXY_TARGET: http://backend:8080
    depends_on:
      - backend

volumes:
  pgdata:
```

- [ ] **Step 2: Bring the stack up**

Run: `docker compose up --build`
Expected: all three services start; the backend logs `Started HappyTradeApplication`.

- [ ] **Step 3: Verify both endpoints end to end**

Run: `curl -s "http://localhost:8080/api/market/ticker?symbol=BTCUSDT"`
Expected: JSON with a non-zero `price` and an ISO-8601 `timestamp`.

Run: `curl -s "http://localhost:8080/api/market/chart?symbol=BTCUSDT&interval=1h&limit=500" | head -c 400`
Expected: JSON beginning with `{"symbol":"BTCUSDT","interval":"1h","candles":[...`

Run: `curl -s "http://localhost:8080/api/market/chart?interval=3m"`
Expected: HTTP 400 with `{"code":"INVALID_PARAMETER",...}`

If the ticker call returns `UPSTREAM_BLOCKED`, Binance is not reachable from this network. That is the documented geo-block path, not a bug in the code — note it and continue.

- [ ] **Step 4: Verify the page in a browser**

Open `http://localhost:5173`.
Expected: the four-pane chart renders and the price updates without a manual reload.

- [ ] **Step 5: Commit**

```bash
git add docker-compose.yml
git commit -m "feat: add Docker Compose stack for db, backend, and frontend"
```

---

### Task 14: Governance records

**Files:**
- Create: `docs/adr/0002-market-data-and-charting-stack.md`
- Modify: `docs/adr/README.md` (append a row to the index table)
- Modify: `CHANGELOG.md` (add entries under `[Unreleased]` / `Added`)

**Interfaces:**
- Consumes: every decision made in Tasks 1–13.
- Produces: nothing consumed by code.

- [ ] **Step 1: Create the ADR**

This decision qualifies on two counts from `docs/adr/README.md` — introducing an external API and introducing third-party libraries. They are recorded together because they are coupled: choosing lightweight-charts means the backend must own the data, which is what forces a real upstream source.

```markdown
# 0002. Market Data Source and Charting Stack

* Status: accepted
* Date: 2026-08-18

## Context and Problem Statement

The market page needs BTC price and OHLCV data, a chart capable of rendering candlesticks with
overlaid moving averages and separate indicator panes, and a place to compute indicators. The
project scope also includes AI signals, so whichever component owns the price data determines
what the signal engine can later be built on.

TradingView was considered first because it is the reference experience for this kind of page.
It offers no public market-data API, so it can supply the chart but never the data.

## Decision Drivers

* The backend must hold OHLCV, because the planned AI-signal engine has to compute over it.
* The "no direct automatic order execution" red line must be enforced structurally, not by
  convention.
* Indicator logic should exist in exactly one place so the chart and future signals cannot drift.
* First slice should stay small enough to verify end to end.

## Considered Options

* Binance public REST API + lightweight-charts, indicators computed in the backend
* TradingView Advanced Real-Time Chart widget (embedded iframe)
* TradingView Charting Library (self-hosted) + a separate data source
* Coinbase public API + lightweight-charts

## Decision Outcome

Chosen Option: "Binance public REST API + lightweight-charts, indicators computed in the
backend", because it is the only option that puts OHLCV in the backend while still giving the
TradingView-family chart experience.

The TradingView widget was rejected because it is an opaque iframe: the backend would never see
a single price, leaving the AI-signal slice with no data to compute over. The TradingView
Charting Library was rejected for this slice because it requires a licence application and a
bespoke UDF datafeed server, and it still requires choosing an upstream data source anyway.
Coinbase remains a viable fallback if Binance becomes unreachable; the `MarketDataProvider`
interface exists so that swap touches one class.

### Safety

The Binance client calls public market-data endpoints only. It sends **no API key and performs
no request signing**, so it is structurally incapable of placing an order — the red line is
enforced by the absence of credentials rather than by discipline. Any future change that
introduces a Binance API key is a red-line change requiring its own ADR and explicit review.

### Positive Consequences

* The backend owns OHLCV, so the AI-signal engine can reuse the `indicator` package unchanged.
* Indicator logic lives in one dependency-free package and is unit-testable against fixed data.
* No API credentials exist anywhere in the system.
* lightweight-charts is Apache-2.0 and roughly 45KB, with candlesticks and histograms as native
  series types.

### Negative Consequences

* Binance blocks some regions and cloud provider IP ranges; when that happens the backend returns
  `UPSTREAM_BLOCKED` and the page cannot show data from that network.
* Indicators must be re-fetched from the backend when parameters change, unlike a client-side
  implementation.
* Every indicator has to be implemented and validated by hand, rather than inherited from a
  charting library's built-in set.
```

- [ ] **Step 2: Add the ADR index row**

In `docs/adr/README.md`, replace:

```markdown
| [0001](0001-initial-system-architecture.md) | Initial System Architecture | accepted |
```

with:

```markdown
| [0001](0001-initial-system-architecture.md) | Initial System Architecture | accepted |
| [0002](0002-market-data-and-charting-stack.md) | Market Data Source and Charting Stack | accepted |
```

- [ ] **Step 3: Update the changelog**

In `CHANGELOG.md`, replace:

```markdown
### Added

- Initial project structure and governance guidelines.
- Base ADR setup and architecture baseline.
```

with:

```markdown
### Added

- Initial project structure and governance guidelines.
- Base ADR setup and architecture baseline.
- Spring Boot backend skeleton with Docker Compose stack (`db`, `backend`, `frontend`).
- Keyless Binance public market data provider (no API key, no request signing).
- Dependency-free indicator package: SMA, EMA, RSI (Wilder), and MACD.
- `GET /api/market/ticker` and `GET /api/market/chart` endpoints with upstream error mapping
  and Caffeine caching.
- BTC market page with a four-pane lightweight-charts view (price, volume, RSI, MACD),
  interval switching, and indicator toggles.
```

- [ ] **Step 4: Verify the full suite still passes**

Run: `cd backend && ./mvnw test`
Expected: all backend tests pass.

Run: `cd frontend && npm test && npx tsc -b`
Expected: all frontend tests pass, type check clean.

- [ ] **Step 5: Commit**

```bash
git add docs/adr CHANGELOG.md
git commit -m "docs: add ADR-0002 for market data and charting stack"
```

---

## Verification Checklist

Against the spec's success criteria:

- [ ] `docker compose up` brings up all three services (Task 13).
- [ ] `GET /api/market/ticker?symbol=BTCUSDT` returns live price and 24h statistics (Task 7, verified in Task 13).
- [ ] `GET /api/market/chart?symbol=BTCUSDT&interval=1h&limit=500` returns 500 candles plus index-aligned indicator series (Tasks 6–7).
- [ ] `http://localhost:5173` renders a four-pane chart that refreshes without a manual reload (Tasks 11–12).
- [ ] Indicator unit tests pass against a fixed dataset with known expected values (Tasks 3–4).
- [ ] No API key or request signing exists anywhere in the codebase (asserted by a test in Task 5).
