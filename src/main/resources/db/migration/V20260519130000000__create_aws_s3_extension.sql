DO $$
BEGIN
    EXECUTE 'CREATE EXTENSION IF NOT EXISTS aws_s3 CASCADE';
EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'aws_s3 extension is not available in this environment (%): %', SQLSTATE, SQLERRM;
END
$$;
