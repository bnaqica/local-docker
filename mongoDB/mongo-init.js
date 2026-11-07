// Runs once when the container is first created.
// Each service gets its own database and a user that owns it.

const services = [
  { name: 'kafka_demo_email_db', user: 'kafka_demo_email_local' },
  { name: 'kafka_demo_analytics_db', user: 'kafka_demo_analytics_local' },
];

for (const svc of services) {
  db.getSiblingDB(svc.name).createUser({
    user: svc.user,
    pwd: 'local',
    roles: [{ role: 'dbOwner', db: svc.name }],
  });
  print(`Created user ${svc.user} for ${svc.name}`);
}
