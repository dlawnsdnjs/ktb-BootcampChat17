import { afterEach, describe, expect, it, vi } from 'vitest';
import axios from 'axios';
import axiosInstance from '../axios';
import fileService from '../fileService';

const image = {
  name: 'photo.png',
  type: 'image/png',
  size: 12,
};

describe('presigned direct upload', () => {
  afterEach(() => {
    fileService.usesS3Storage = false;
    fileService.activeUploads.clear();
    vi.restoreAllMocks();
  });

  it('uploads a chat file through presign, S3 PUT, and completion', async () => {
    fileService.usesS3Storage = true;
    vi.spyOn(fileService, 'validateFile').mockResolvedValue({ success: true });
    const post = vi.spyOn(axiosInstance, 'post')
      .mockResolvedValueOnce({
        data: {
          uploadId: 'upload-1',
          uploadUrl: 'https://s3.example/upload',
          requiredHeaders: { 'Content-Type': 'image/png' },
        },
      })
      .mockResolvedValueOnce({
        data: {
          success: true,
          file: { filename: 'stored.png', mimetype: 'image/png' },
        },
      });
    const put = vi.spyOn(axios, 'put').mockResolvedValue({ status: 200 });

    const result = await fileService.uploadFile(image);

    expect(post).toHaveBeenNthCalledWith(1, '/api/uploads/presign', {
      purpose: 'CHAT_ATTACHMENT',
      originalName: 'photo.png',
      contentType: 'image/png',
      size: 12,
    }, expect.any(Object));
    expect(put).toHaveBeenCalledWith(
      'https://s3.example/upload', image, expect.objectContaining({
        headers: { 'Content-Type': 'image/png' },
        withCredentials: false,
      })
    );
    expect(post).toHaveBeenNthCalledWith(
      2, '/api/files/upload/complete', { uploadId: 'upload-1' }, expect.any(Object)
    );
    expect(result.success).toBe(true);
    expect(result.data.file.url).toContain('/api/files/view/stored.png');
  });

  it('keeps the legacy multipart profile API in local storage mode', async () => {
    const post = vi.spyOn(axiosInstance, 'post').mockResolvedValue({
      data: { success: true, imageUrl: '/api/files/profiles/stored.png' },
    });

    const result = await fileService.uploadProfileImage(image);

    expect(post).toHaveBeenCalledWith(
      '/api/users/profile-image', expect.any(FormData), expect.any(Object)
    );
    expect(result.imageUrl).toBe('/api/files/profiles/stored.png');
  });

  it('completes a direct profile image upload with the existing response shape', async () => {
    fileService.usesS3Storage = true;
    const post = vi.spyOn(axiosInstance, 'post')
      .mockResolvedValueOnce({
        data: {
          uploadId: 'upload-2',
          uploadUrl: 'https://s3.example/profile',
          requiredHeaders: { 'Content-Type': 'image/png' },
        },
      })
      .mockResolvedValueOnce({
        data: { success: true, imageUrl: '/api/files/profiles/stored.png' },
      });
    vi.spyOn(axios, 'put').mockResolvedValue({ status: 200 });

    const result = await fileService.uploadProfileImage(image);

    expect(post).toHaveBeenNthCalledWith(
      2, '/api/users/profile-image/complete', { uploadId: 'upload-2' }, expect.any(Object)
    );
    expect(result.imageUrl).toBe('/api/files/profiles/stored.png');
  });
});
