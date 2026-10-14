# Health Data Delivery

Health Connect records prepared for configured webhooks with data-type selection and resolution.

## Language

**Health data type**:
A supported category of data read from Health Connect.

**Webhook configuration**:
A named, enabled or disabled destination for selected health data, with associated notification settings.

**Data-type filter**:
The subset of globally enabled health types assigned to a webhook. Without an explicit filter, the webhook follows the global selection.

**Record metadata**:
The origin, recording method and optional device provenance attached to a health record.

**Data resolution**:
The representation granularity selected for a health type: daily aggregate, raw records, time buckets or sleep summary.

**Sleep stage**:
A stage interval within a sleep session, distinct from the session summary.

**Scheduled sync**:
A daily scheduled occasion for synchronizing health data that can be enabled or disabled independently.

**Notification configuration**:
A saved provider-specific notification destination referenced by webhook configurations.
