const targetDbName = process.env.PAYMENT_DB_NAME;
const dbUser = process.env.PAYMENT_DB_USERNAME;
const dbPassword = process.env.PAYMENT_DB_PASSWORD;

db = db.getSiblingDB(targetDbName);

db.createUser({
    user: dbUser,
    pwd: dbPassword,
    roles: [
        {
            role: 'dbOwner',
            db: targetDbName
        }
    ]
});