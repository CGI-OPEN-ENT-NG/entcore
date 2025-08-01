import { odeServices } from '@edifice.io/client';

/**
 * Creates a message attachment service with the specified base URL.
 *
 * @param baseURL The base URL for the folder service API.
 * @returns A service to interact with folders.
 */
export const createAttachmentService = (baseURL: string) => ({
  attach(
    messageId: string,
    payload: File | Blob,
  ): Promise<{
    id: string;
  }> {
    const formData = new FormData();
    formData.append('file', payload);
    return odeServices.http().postFile<{
      id: string;
    }>(`${baseURL}/message/${messageId}/attachment`, formData);
  },

  detach(
    messageId: string,
    attachmentId: string,
  ): Promise<{
    fileId: string;
    fileSize: number;
  }> {
    return odeServices.http().delete<{
      fileId: string;
      fileSize: number;
    }>(`${baseURL}/message/${messageId}/attachment/${attachmentId}`);
  },

  async downloadBlob(messageId: string, attachmentId: string): Promise<Blob> {
    const downloadUrl = `${baseURL}/message/${messageId}/attachment/${attachmentId}`;
    const result = await odeServices.http().get(downloadUrl, {
      responseType: 'blob',
    });
    return result;
  },
});
