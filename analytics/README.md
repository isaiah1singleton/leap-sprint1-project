# LEAP Trading Analytics

This directory contains the Databricks analytical implementation for the LEAP Direct Trading Platform.

The design supports BR-16: analyse trading activity over time, by instrument, and by client segment without analytical workloads competing with the live trading system.

## Architecture

```text
PostgreSQL OLTP
      |
      | asynchronous ingestion
      v
Databricks Bronze
      |
      v
Databricks Silver
      |
      v
Databricks Gold
      |
      v
Analytics
```

PostgreSQL remains the system of record for live trading. Databricks handles analytical processing.

## Bronze

Bronze contains raw data ingested from PostgreSQL. The initial implementation uses:

- clients
- accounts
- instruments
- orders
- fills

For the project prototype, these can be exported from PostgreSQL and loaded manually. This can later be replaced by automated ingestion or CDC.

## Silver

`leap.silver.executed_trades` joins the relevant Bronze tables into a clean analytical dataset.

**Grain:** one row per executed fill.

It derives:

```text
notional_amount = executed_quantity * execution_price
```

## Gold

Gold implements the reporting star schema.

Fact:
- `fact_trade`

Dimensions:
- `dim_client`
- `dim_account`
- `dim_instrument`
- `dim_date`
- `dim_time`

The grain of `fact_trade` is one row per executed fill. Source OLTP identifiers are retained where appropriate for lineage.

## BR-16

The queries directory demonstrates analysis by:
- period
- instrument
- client segment

These queries run against Databricks rather than the PostgreSQL transactional database.

## Currency

Trade notional is denominated in the instrument's quote currency. Amounts in different currencies must not be treated as one monetary total without FX conversion.

## Consistency and availability

The analytical store is derived from PostgreSQL and is eventually consistent. PostgreSQL remains authoritative for live balances, holdings, orders, and trade execution.

Failure or delay of the analytics pipeline must not prevent users from trading.
