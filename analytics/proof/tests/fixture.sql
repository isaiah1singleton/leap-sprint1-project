-- Seed-equivalent analytical projections; no source database needed.
INSERT INTO bronze.clients VALUES (1,'RETAIL'),(2,'RETAIL'),(3,'PREMIUM'),(4,'PROFESSIONAL');
INSERT INTO bronze.accounts VALUES (1,1),(2,2),(3,3),(4,4);
INSERT INTO bronze.instruments VALUES
(1,'AAPL','Apple Inc.','EQUITY','NASDAQ','USD'),
(2,'MSFT','Microsoft Corporation','EQUITY','NASDAQ','USD'),
(3,'BARC','Barclays PLC','EQUITY','LSE','GBP'),
(4,'GBPUSD','British Pound / US Dollar','FX','FX','USD'),
(5,'BTCUSD','Bitcoin / US Dollar','CRYPTO','CRYPTO','USD');
INSERT INTO bronze.orders VALUES
(1,1,1,'BUY',10),(2,1,2,'BUY',5),(3,1,1,'SELL',2),
(4,2,3,'BUY',50),(5,2,1,'BUY',20),(6,3,5,'BUY',0.10),
(7,3,4,'BUY',1000),(8,3,2,'BUY',8),(9,4,1,'BUY',100),
(10,4,3,'BUY',500),(11,4,5,'BUY',0.25),(12,4,1,'SELL',25);
INSERT INTO bronze.fills VALUES
(1,1,225.10,'2026-08-28 14:00:05+00'),
(2,2,509.80,'2026-09-03 15:00:05+00'),
(3,3,230.40,'2026-10-01 14:30:05+00'),
(4,4,3.18,'2026-09-10 09:00:05+00'),
(5,5,228.25,'2026-10-02 15:00:05+00'),
(6,6,64100,'2026-08-30 18:00:05+00'),
(7,7,1.3420,'2026-09-15 12:00:05+00'),
(8,8,514.75,'2026-10-03 16:00:05+00'),
(9,9,226.15,'2026-09-20 14:00:05+00'),
(10,10,3.27,'2026-09-21 10:00:05+00'),
(11,11,65200,'2026-10-04 17:00:05+00'),
(12,12,232.20,'2026-10-05 14:00:05+00');
