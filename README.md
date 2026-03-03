# Smart Treasury

## What Problem It Solves
Imagine you're a startup with 10 contractors: 3 in India, 3 in Germany, 2 in Brazil, 2 in UK. Every month you're paying invoices in INR, EUR, BRL, GBP. You're doing this manually, invoice arrives, you pay it that day, in whatever currency, through whatever bank.

You're losing money in two ways:

1. **Bad timing:** EUR might be 2% cheaper to buy on Thursdays vs Mondays historically. You're not tracking that.
2. **Death by transaction fees:** paying 5 INR invoices separately = 5 × $8 fee. Paying them together = 1 × $8 fee. You just wasted $32.

This app fixes both: 

### 1. /dashboard: The Overview
The first thing you see when you log in. Answers the question "where does my money stand right now?"

* **Summary cards:** Total money you owe (converted to USD), how many invoices are due this week, how much you could save by optimizing
* **Cash flow timeline:** A chart showing the next 30 days of outgoing payments. You can see "oh, March 15th is a brutal week"
* **FX Rate Heatmap:** A table where rows are currency pairs (USD→INR, USD→EUR etc.) and columns are days of the week. Cells are green (cheap day to pay) or red (expensive day). Built from 90 days of historical data.
* **Alert banner:** "EUR is 2.3% below its 90-day average right now, good window to pay EUR invoices"

### 2. /vendors: Who You're Paying
Simple. Add your contractors/vendors with their country and preferred currency. This feeds the invoice creation flow.

### 3. /invoices: What You Owe
A table of every invoice, vendor name, amount, currency, USD equivalent, due date, status (Pending / Scheduled / Paid). You add invoices here manually. This is your raw data input.

### 4. /payment-queue: THE HERO SCREEN
This is the whole point of the app. You hit "Optimize Payments" and the backend runs the batching engine. It comes back with something like:

**Batch #1 — 3 INR Invoices — Recommended: Pay Thursday Mar 7**
* Acme Dev — ₹80,000 ($960)
* Dev Studio — ₹45,000 ($540) 
* Ravi K. — ₹30,000 ($360)
* **Total: $1,860 | If paid individually today: $2,127 | You save: $267 ✅**

**Batch #2 — 2 EUR Invoices — Recommended: Pay Friday Mar 8** * Studio X — €2,400 ($2,592)
* Klaus GmbH — €800 ($864)
* **Total: $3,456 | If paid individually today: $3,521 | You save: $65 ✅**

You review it, hit "Approve Batch", and it gets scheduled. Hit "Execute" when the day comes and all those invoices flip to Paid.

### 5. /fx-rates: Currency Intelligence
Live FX rate tickers, 90-day line charts per currency pair, volatility scores (Low/Medium/High), and a "best day to pay" callout per currency. More of an analytical/reference screen.

---

## How the Backend Works

### The FX Poller (CRON job, every hour)
A Kotlin coroutine wakes up every hour, hits the Frankfurter API (free, no key), grabs the latest USD→EUR/INR/GBP/BRL rates, stores them in:

* **Redis:** for fast real-time reads (the frontend's live ticker)
* **PostgreSQL:** as a historical snapshot (for the volatility calculations)

After 90 days of this running, you have a rich dataset of how currencies move.

### The Batching Engine — BatchingEngine.kt
This is the brain of the app. Here's the logic step by step:

1. Grab all PENDING invoices from the DB
2. Group them by currency (all INR together, all EUR together, etc.)
3. For each currency group, calculate two scores:
    * **RATE SCORE (0-1):**
        * Pull 90-day history for that currency pair
        * Is today's rate better or worse than the 90-day average?
        * "USD/INR is 1.8% below average" = high rate score (good time to pay INR)
    * **URGENCY SCORE (0-1):** * What's the earliest due date in this group?
        * If something is due in 2 days, urgency is high regardless of rate
4. Final score = (rate_score × 0.6) + (urgency_score × 0.4)
5. Sort all groups by score
6. Assign recommended payment dates greedily — never push past a due date
7. Calculate estimated saving = (individual transaction fees) - (batched fee) + (FX rate advantage × total amount)
8. Return proposed batches

It's a greedy algorithm.

### Volatility Calculation
For any currency pair over 90 days:
* Calculate the daily % change each day
* Standard deviation of those changes = volatility score
* Group by day of week, average the rates → find the cheapest day historically

---

## How Data Flows End-to-End

1. **Frankfurter API**
    * ↓ (every hour, CRON)
2. **Spring Boot Scheduler**
    * ↓
3. **Redis (cache) + PostgreSQL (history)**
    * ↓
4. **REST APIs**
    * ↓
5. **Next.js Frontend**
    * ↓
6. **User sees live rates + approves batches**
    * ↓
7. **Batch approved → PostgreSQL updated → invoices marked SCHEDULED**
    * ↓
8. **User executes → invoices marked PAID**