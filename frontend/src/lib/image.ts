/**
 * Shrinks a phone photo before upload: the long edge is capped at `maxSide` px and the image is
 * re-encoded as JPEG. A 12 MP photo (4–10 MB) becomes a few hundred KB — still sharp enough to read
 * handwriting, and well under the backend's 8 MB limit. Anything the browser cannot decode (e.g.
 * HEIC on some desktops) is returned untouched and the server decides.
 */
export async function shrinkImage(file: File, maxSide = 2000, quality = 0.85): Promise<File> {
  if (!file.type.startsWith('image/') || file.type === 'image/gif') {
    return file
  }
  try {
    const bitmap = await createImageBitmap(file)
    const scale = Math.min(1, maxSide / Math.max(bitmap.width, bitmap.height))
    if (scale === 1 && file.size <= 2 * 1024 * 1024) {
      bitmap.close()
      return file
    }
    const canvas = document.createElement('canvas')
    canvas.width = Math.round(bitmap.width * scale)
    canvas.height = Math.round(bitmap.height * scale)
    const context = canvas.getContext('2d')
    if (!context) {
      bitmap.close()
      return file
    }
    context.drawImage(bitmap, 0, 0, canvas.width, canvas.height)
    bitmap.close()
    const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/jpeg', quality))
    // An already well-compressed original (e.g. a flat PNG scan) can come out larger as JPEG;
    // the point is fewer bytes over mobile data, so keep whichever is smaller.
    if (!blob || blob.size >= file.size) {
      return file
    }
    return new File([blob], file.name.replace(/\.[^.]+$/, '') + '.jpg', { type: 'image/jpeg' })
  } catch {
    return file
  }
}
