-- HP-MT-02: Tenant suspension lifecycle (blocks login & live sessions without deleting data).
ALTER TABLE clients ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE clients ADD COLUMN IF NOT EXISTS suspended_at TIMESTAMPTZ;

-- Sync orders payment_method check on pre-TOKEN databases created under ddl-auto: update.
ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_payment_method_check;
ALTER TABLE orders ADD CONSTRAINT orders_payment_method_check
    CHECK (payment_method IN ('CASH', 'CARD', 'TOKEN'));
