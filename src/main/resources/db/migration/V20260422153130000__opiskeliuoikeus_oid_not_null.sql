UPDATE tiedote
SET opiskeluoikeus_oid = '[uupuu]'
WHERE opiskeluoikeus_oid IS NULL;
ALTER TABLE tiedote ALTER COLUMN opiskeluoikeus_oid SET NOT NULL;
