import { request } from '../../api/client.ts'

/** Eine Test-Mail (GraphMailTestSendRequest.java): Absender und Empfänger gibt der Admin selbst an */
export interface GraphMailTestSendRequest {
  /** das Postfach im Tenant, in dessen Namen verschickt wird */
  senderAddress: string
  recipientEmail: string
  subject: string
  /** der Text als HTML */
  htmlBody: string
}

// ===== Endpunkt des GraphMailTestSendAdminController =====

const GRAPH_MAIL_TEST_SEND_ADMIN_PATH = '/api/rest/v1/admin/graph/testmail'

/** 200 ohne Inhalt, wenn Graph die Mail angenommen hat, 503 wenn Graph ablehnt */
export function sendGraphTestMail(testMail: GraphMailTestSendRequest) {
  return request<void>('POST', GRAPH_MAIL_TEST_SEND_ADMIN_PATH, testMail)
}
