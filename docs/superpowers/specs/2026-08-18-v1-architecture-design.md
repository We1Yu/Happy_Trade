# Happy_Trade v1 System Architecture

* Date: 2026-08-18
* Status: proposed
* Scope: The complete first version — market page, AI signal page, simulated trading, positions
  and performance, and risk reminders. Supersedes nothing; extends ADR-0001 from a stack choice
  into a module-level design.

## 1. Goal

Define the module boundaries, persistence model, API surface, and build order for v1, so each
feature slice can be specced and implemented independently without renegotiating the whole
system every time.

This document is a design, not a plan. It does not contain implementation steps. Each slice in
§9 gets its own spec and its own implementation plan.

## 2. Safety Red Line

**No direct automatic order execution.** In v1 this is enforced structurally, not by convention:

* The codebase contains **no broker or exchange trading client**, no API key, and no request
  signing anywhere.
* The **only** permitted outbound host is `api.binance.com`, and only its public market-data
  endpoints.
* The word "order" in this system always means a row written to Postgres. It never means an
  outbound HTTP request.
* Automatic stop-loss and take-profit closing (§6.3) acts **only on rows in the local simulated
  account**. It is not an exception to the red line, because there is nothing outside the
  database for it to act on.

Any change that introduces a credential, a request signature, or a new outbound host is a
red-line change requiring its own ADR and explicit review before merge.

## 3. Service Topology

Unchanged from ADR-0001. Three Docker Compose services:

| Service | Stack | Port | Role |
|---|---|---|---|
| `frontend` | React + TypeScript + Vite | 5173 | SPA. Proxies `/api` to the backend. |
| `backend` | Java 21 + Spring Boot | 8080 | All logic. Sole owner of the database. |
| `db` | PostgreSQL 16 | 5432 | Trades, positions, and signal history. |

The market slice deliberately ships with no datasource (see the market page design §3). Slice 2
introduces JPA and connects `backend` to `db` for the first time. That is a persistence-layer
change and therefore needs its own ADR.

## 4. Backend Module Boundaries

A modular monolith. One deployable, five feature packages under `com.happytrade`, each owning
its own model, logic, persistence, and web layer.

| Module | Owns | Depends on |
|---|---|---|
| `market` | Binance access, candles, tickers, indicator maths | — |
| `signal` | Rule-based signal generation, signal history | `market` |
| `trading` | Virtual account, positions, trade log, stop engine | `market` (via port) |
| `performance` | Win rate, cumulative return, drawdown | `trading` (read-only) |
| `risk` | Risk rule evaluation and warnings | `trading`, `market` (via port) |

Dependencies point in one direction only. `market` knows about nothing else and must stay that
way, because `market.indicator` is reused verbatim by `signal`.

### 4.1 The price port

`trading` and `risk` need the current price, but must never learn how to talk to Binance. They
depend on a narrow interface owned by `market`:

```java
public interface PriceSource {
    double currentPrice(String symbol);
}
```

`market` provides the single implementation, backed by the existing 3-second ticker cache. This
keeps the dependency one-way and means the stop engine polling every position costs no extra
upstream calls.

Introducing `PriceSource` is a small additive change to the already-shipped market slice.

## 5. Data Model

PostgreSQL. All monetary and quantity columns are `numeric(20,8)`, never floating point —
`double` accumulates error across repeated PnL arithmetic, and a balance that drifts by cents is
worse than useless in a tool whose entire purpose is measuring performance.

### 5.1 `account`

Single row in v1, but modelled as a table so that a second account is a row insert rather than
a schema migration.

| Column | Type | Notes |
|---|---|---|
| `id` | bigserial PK | |
| `name` | text not null | |
| `initial_balance` | numeric(20,8) not null | The starting virtual capital. Never mutated — the drawdown baseline depends on it. |
| `cash_balance` | numeric(20,8) not null | Free cash. Decreases when a position opens, increases when it closes. |
| `created_at` | timestamptz not null | |

### 5.2 `position`

Holds both open and closed positions. One row per position, mutated on close.

| Column | Type | Notes |
|---|---|---|
| `id` | bigserial PK | |
| `account_id` | bigint FK → `account` | |
| `symbol` | text not null | `BTCUSDT` in v1 |
| `side` | text not null | `LONG` \| `SHORT` |
| `quantity` | numeric(20,8) not null | |
| `entry_price` | numeric(20,8) not null | |
| `stop_loss` | numeric(20,8) null | null means no stop set — this is what the risk reminder detects |
| `take_profit` | numeric(20,8) null | |
| `status` | text not null | `OPEN` \| `CLOSED` |
| `opened_at` | timestamptz not null | |
| `closed_at` | timestamptz null | |
| `exit_price` | numeric(20,8) null | The price actually filled at, not the trigger price (§6.3) |
| `exit_reason` | text null | `MANUAL` \| `STOP_LOSS` \| `TAKE_PROFIT` |
| `realized_pnl` | numeric(20,8) null | Frozen at close. Never recomputed. |

Index on `(account_id, status)` — the stop engine and the positions page both read open
positions on every tick.

`realized_pnl` is stored rather than derived so that historical rows stay correct even if the
PnL formula is later changed or a fee model is introduced.

### 5.3 `trade_event`

Append-only. Never updated, never deleted.

| Column | Type | Notes |
|---|---|---|
| `id` | bigserial PK | |
| `account_id` | bigint FK → `account` | |
| `position_id` | bigint FK → `position` | |
| `type` | text not null | `OPEN` \| `CLOSE` \| `MODIFY_PROTECTION` |
| `price` | numeric(20,8) not null | |
| `quantity` | numeric(20,8) not null | |
| `occurred_at` | timestamptz not null | |
| `note` | text null | e.g. which rule triggered the close |

`position` is mutable current state; `trade_event` is the audit trail. Keeping both is not
redundancy — once a position row is mutated on close, the sequence of what happened is
unrecoverable, and "交易紀錄" is one of the things v1 must show.

### 5.4 `signal`

| Column | Type | Notes |
|---|---|---|
| `id` | bigserial PK | |
| `symbol` | text not null | |
| `interval` | text not null | Which timeframe the signal was computed on |
| `direction` | text not null | `LONG` \| `SHORT` \| `NEUTRAL` |
| `confidence` | numeric(5,2) not null | 0–100 |
| `horizon` | text not null | The prediction window, e.g. `4h` |
| `rationale` | text not null | Human-readable, generated from the contributing rules |
| `data_as_of` | timestamptz not null | Close time of the last candle used |
| `generated_at` | timestamptz not null | When the backend computed it |
| `rule_version` | text not null | Which ruleset produced it |

Unique constraint on `(symbol, interval, data_as_of, rule_version)`.

Two columns carry real weight here. `data_as_of` is separate from `generated_at` because the
user asked to see the **data time**, and a signal recomputed at 14:05 from a candle that closed
at 14:00 is a statement about 14:00. `rule_version` exists because signal history is worthless
if you cannot tell which ruleset produced a given row — without it, changing a threshold
silently invalidates every past signal.

The unique constraint makes generation idempotent: a rule-based signal is a pure function of
its inputs, so repeated computation over the same candle must not create duplicate rows.

## 6. Feature Design

### 6.1 Market page

Already specified in `2026-08-17-market-page-design.md`. No changes.

### 6.2 AI signal page — rule-based indicator voting

Signals are computed from the existing `market.indicator` package. No LLM, no external call, no
API key. The generator is a pure function:

```
(candles, indicator series) -> Signal
```

Each rule votes with a signed weight, the votes are summed, and the total is mapped to a
direction and a confidence score. Every rule that fired contributes one clause to the rationale,
which is how "簡短理由" is produced without any language model — the reasoning is literally the
list of rules that voted.

Illustrative ruleset (exact thresholds and weights are settled in the slice-5 spec):

| Rule | Votes |
|---|---|
| Price above / below SMA200 | Trend bias |
| EMA15 above / below EMA60 | Short-term trend |
| RSI14 above 70 / below 30 | Mean-reversion pressure |
| MACD histogram sign flip | Momentum turn |

Three properties make this the right choice for v1: it is **reproducible** (the same candle
always yields the same signal, which is what makes `rule_version` meaningful), **testable**
against fixed datasets exactly like the indicators are, and **free**, so it can run on a
schedule without a cost ceiling.

Signals are generated on a 60-second schedule and persisted. Generation is also triggered on
read if no signal exists yet for the current candle. Because of the unique constraint, both
paths are safe to run concurrently.

### 6.3 Simulated trading

Opening a position: validate, take the current price from `PriceSource`, debit
`quantity × price` from `cash_balance`, insert a `position` row and an `OPEN` trade event, in
one transaction.

Closing is the mirror: credit the proceeds, freeze `realized_pnl`, set `status = CLOSED`, insert
a `CLOSE` event.

**The stop engine.** A `@Scheduled` task runs every 15 seconds, loads all open positions, groups
them by symbol, reads one price per symbol through the cached `PriceSource`, and closes any
position whose stop-loss or take-profit is breached.

The important honesty point: **positions fill at the observed price, not at the stop price.**
Polling means the price can gap past a stop between two checks. Filling at the stop price would
be the flattering choice, and it would quietly overstate every performance number the dashboard
reports. Filling at the observed price occasionally shows a loss worse than the stop — which is
exactly what would happen in a real market, and exactly what a tool for learning risk should
show.

Not in v1: fees, slippage models, partial fills, leverage, margin calls. Adding a fee model
later only changes how `realized_pnl` is computed at close, and stored historical rows keep
their original values.

### 6.4 Positions and performance

All metrics are computed on read from `position` rows. No materialised statistics table — a
personal account produces hundreds of rows, not millions, and a stored aggregate that can drift
out of sync with its source is a liability at this scale.

| Metric | Definition |
|---|---|
| Unrealised PnL | Over open positions, at the current `PriceSource` price |
| Realised PnL | Sum of `realized_pnl` over closed positions |
| Equity | `cash_balance` + open position market value |
| Win rate | Closed positions with `realized_pnl > 0`, over all closed positions |
| Cumulative return | `(equity − initial_balance) / initial_balance` |
| Max drawdown | Largest peak-to-trough decline of the equity curve |

The equity curve is built from closed positions ordered by `closed_at`, starting at
`initial_balance`. This makes v1's drawdown a **realised** drawdown: it ignores unrealised swings
inside a still-open trade, so a position that fell 40% before recovering to break-even
contributes nothing. This understates true drawdown. Fixing it needs periodic equity snapshots,
which is deliberately deferred — the honest number with a documented limitation is better than a
snapshot table nobody has decided the retention policy for.

### 6.5 Risk reminders

Three rules, thresholds configured in `application.yml`:

| Rule | Check |
|---|---|
| Max loss per trade | `\|entry − stop\| × quantity ≤ maxLossPercent × equity` |
| Position cap | Total open notional `≤ maxExposurePercent × equity` |
| Missing stop | `stop_loss IS NULL` on an open position |

```yaml
happytrade:
  risk:
    max-loss-per-trade-percent: 2
    max-exposure-percent: 50
```

**These warn; they do not block.** The user called them「提醒」, and this is a personal simulated
account whose purpose is learning. A tool that refuses your trade teaches you to work around the
tool; a tool that tells you the trade risks 8% of your account and lets you take it anyway
teaches you what 8% feels like.

The distinction from validation: insufficient cash, non-positive quantity, and an unknown symbol
are **errors** and are rejected outright. Those are impossible states, not risky ones.

Rules run in two places: on the open-position request, returned as warnings alongside the
created position; and on every read of the positions page, so a position that drifts into breach
keeps nagging rather than being judged once at birth.

## 7. API Surface

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/market/ticker` | Live price and 24h stats (shipped) |
| GET | `/api/market/chart` | Candles and indicator series (shipped) |
| GET | `/api/signal/latest` | Current signal for a symbol and interval |
| GET | `/api/signal/history` | Past signals, newest first |
| GET | `/api/account` | Balance, equity, exposure |
| GET | `/api/positions` | Filtered by `status` |
| POST | `/api/positions` | Open a position; returns the position plus risk warnings |
| POST | `/api/positions/{id}/close` | Close manually at the current price |
| PATCH | `/api/positions/{id}/protection` | Set or change stop-loss / take-profit |
| GET | `/api/trades` | The `trade_event` log |
| GET | `/api/performance` | Win rate, returns, drawdown |
| GET | `/api/risk/warnings` | Standing warnings across all open positions |

No endpoint in this table reaches a broker. Every write lands in Postgres.

## 8. Frontend Structure

```
src/
  shared/           API client, formatting, shared types
  components/       RiskBanner, and other cross-cutting UI
  features/
    market/         (shipped)
    signal/
    trading/
    portfolio/
```

Four routes: 行情 / AI 訊號 / 模擬交易 / 持倉績效. Risk warnings are not a page — they are a
banner component rendered above the trading and portfolio views, because a warning on its own
screen is a warning nobody reads.

Polling stays the transport, consistent with the market slice. Positions and performance poll on
a 10-second interval; signals on 60 seconds, matching their generation schedule.

## 9. Build Order

| Slice | Contents | Depends on |
|---|---|---|
| 1 | Market page | — |
| 2 | Persistence + simulated trading: JPA, `account` / `position` / `trade_event`, open and close, `PriceSource`, stop engine | 1 |
| 3 | Positions and performance read models | 2 |
| 4 | Risk reminders | 2, 3 |
| 5 | AI signal page | 1 |

Slice 2 is the largest and carries the most architectural risk, because it introduces the
database, transactions, and the scheduler all at once. Slices 3 and 4 are small — they add read
models and rule evaluation over a schema that already exists.

Slice 5 depends only on slice 1 and could be built at any point after it. It is ordered last
because a signal you cannot act on is a display, and acting on it requires slice 2.

## 10. Governance

ADRs required as each slice lands:

| ADR | Trigger | Slice |
|---|---|---|
| 0002 | External API (Binance) and third-party libraries (lightweight-charts, Caffeine) | 1 |
| 0003 | Persistence layer and trading domain schema | 2 |
| 0004 | Risk rule definitions and the warn-not-block decision | 4 |

ADR-0002 is already specified in the market page implementation plan, Task 14. Numbering here
reserves 0003 and 0004 so the slices do not collide.

Every slice updates `CHANGELOG.md` under `[Unreleased]`, per `CLAUDE.md`.

## 11. Out of Scope for v1

* Multi-symbol trading — the schema carries `symbol`, but the UI is BTCUSDT only.
* Multiple accounts — the schema supports it, the UI does not expose it.
* Authentication — single user, localhost, no login.
* Fees, slippage, leverage, margin, partial fills.
* Backtesting the ruleset over history.
* WebSocket streaming — everything polls.
* Equity snapshots for unrealised drawdown (see §6.4).
