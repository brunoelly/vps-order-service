UPDATE orders SET status = 'PENDING' WHERE status = 'PENDENTE';
UPDATE orders SET status = 'APPROVED' WHERE status = 'APROVADO';
UPDATE orders SET status = 'PROCESSING' WHERE status = 'EM_PROCESSAMENTO';
UPDATE orders SET status = 'SHIPPED' WHERE status = 'ENVIADO';
UPDATE orders SET status = 'DELIVERED' WHERE status = 'ENTREGUE';
UPDATE orders SET status = 'CANCELLED' WHERE status = 'CANCELADO';
