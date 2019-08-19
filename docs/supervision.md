---
id: supervision
title: "Supervision"
---

# Supervision

A Supervisors responsability is to manage actors.

- Makes sure all messages in the queue are processed
- Has retry logic to manage failures
- Takes the next available message and start actor to process it
- puts failed actors back on the queue for reprocessing whilst respecting retry
    preferences


