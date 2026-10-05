package org.eclipse.cargotracker.interfaces.handling.file;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Checkpoint for the EventFilesProcessor batch job.
 *
 * <p>Replaces the previous java.io.File-based checkpoint with a cloud-native S3-key-based
 * checkpoint. Instead of tracking local File objects and file pointers (which are meaningless
 * in a cloud/containerised environment), this checkpoint tracks the list of S3 object keys
 * that have been discovered and the index of the key currently being processed.
 *
 * <p>This change is required by rule cr-java-0064 (Local Directory Scanning): all local
 * file-system references have been removed and replaced with Amazon S3 identifiers.
 */
public class EventFilesCheckpoint implements Serializable {

  private static final long serialVersionUID = 2L;

  /** S3 object keys discovered in the upload bucket for the current job execution. */
  private List<String> s3Keys = new ArrayList<>();

  /** Index of the S3 key currently being processed. */
  private int keyIndex = 0;

  // -------------------------------------------------------------------------
  // Accessors
  // -------------------------------------------------------------------------

  public List<String> getS3Keys() {
    return s3Keys;
  }

  /**
   * Sets the full list of S3 object keys to process.
   * Kept for API compatibility with callers that previously called {@code setFiles(...)}.
   */
  public void setFiles(List<String> keys) {
    this.s3Keys = keys != null ? new ArrayList<>(keys) : new ArrayList<>();
  }

  public int getKeyIndex() {
    return keyIndex;
  }

  public void setKeyIndex(int keyIndex) {
    this.keyIndex = keyIndex;
  }

  // -------------------------------------------------------------------------
  // Navigation helpers (mirrors the old currentFile / nextFile contract)
  // -------------------------------------------------------------------------

  /**
   * Returns the S3 key at the current index, or {@code null} when all keys have been consumed.
   */
  public String currentKey() {
    if (s3Keys != null && keyIndex < s3Keys.size()) {
      return s3Keys.get(keyIndex);
    }
    return null;
  }

  /**
   * Advances to the next S3 key and returns it, or {@code null} when exhausted.
   */
  public String nextKey() {
    if (s3Keys != null && ++keyIndex < s3Keys.size()) {
      return s3Keys.get(keyIndex);
    }
    return null;
  }
}
