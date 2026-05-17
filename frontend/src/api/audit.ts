import { getJson } from "./client";

export type AuditEvent = {
  id: number;
  entityType: string;
  entityId: string;
  action: string;
  message: string;
  metadataJson: string | null;
  createdAt: string;
};

export function fetchAuditEvents(limit = 12) {
  return getJson<AuditEvent[]>(`/api/audit-events?limit=${limit}`);
}
