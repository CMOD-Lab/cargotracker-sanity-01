package org.eclipse.cargotracker.interfaces.handling.file;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;

/**
 * Checkpoint that tracks which S3 objects (by key) have been processed and the
 * current read position within the active object.
 *
 * <p>Replaces the original {@code java.io.File}-based checkpoint with a String-key
 * based checkpoint so that the batch job can run in cloud environments where the
 * local file system is ephemeral and files are stored in Amazon S3.
 */
public class EventFilesCheckpoint implements Serializable {

  private static final long serialVersionUID = 1L;

  /** S3 object keys to be processed. */
  private List<String> files = new LinkedList<>();

  /** Index of the S3 object key currently being processed. */
  private int fileIndex = 0;

  /**
   * Line index (0-based) within the current S3 object that has been read so far.
   * Used as a logical "file pointer" for checkpoint/restart.
   */
  private long filePointer = 0;

  public void setFiles(List<String> files) {
    this.files = files;
  }

  public long getFilePointer() {
    return filePointer;
  }

  public void setFilePointer(long filePointer) {
    this.filePointer = filePointer;
  }

  /**
   * Returns the S3 object key currently being processed, or {@code null} if all
   * objects have been processed.
   */
  public String currentFile() {
    if (files.size() > fileIndex) {
      return files.get(fileIndex);
    } else {
      return null;
    }
  }

  /**
   * Advances to the next S3 object key and returns it, or {@code null} if there
   * are no more objects to process.
   */
  public String nextFile() {
    filePointer = 0;

    if (files.size() > ++fileIndex) {
      return files.get(fileIndex);
    } else {
      return null;
    }
  }
}
