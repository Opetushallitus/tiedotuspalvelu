INSERT INTO tiedotestate (tiedotestate_id, description) VALUES
    ('TIEDOTTEEN_JA_OPPIJAN_VALIDOINTI', 'Validoidaan tiedote (postittamiseen liittyvät yhteystiedot) ja oppijan henkilötiedot ennen tiedotteen lähettämistä')
    ON CONFLICT (tiedotestate_id) DO UPDATE SET description = EXCLUDED.description;

UPDATE tiedote
SET tiedotestate_id = 'TIEDOTTEEN_JA_OPPIJAN_VALIDOINTI'
WHERE tiedotestate_id = 'OPPIJAN_VALIDOINTI';

DELETE FROM tiedotestate WHERE tiedotestate_id = 'OPPIJAN_VALIDOINTI';