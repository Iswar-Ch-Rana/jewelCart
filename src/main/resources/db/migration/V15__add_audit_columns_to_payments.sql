-- V15__add_audit_columns_to_payments.sql
ALTER TABLE payments
    ADD COLUMN created_by VARCHAR(100),
    ADD COLUMN updated_by VARCHAR(100);
