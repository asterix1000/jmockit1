/*
 * Copyright (c) 2006 JMockit developers
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.coverage.data;

import java.io.*;
import java.util.*;
import java.util.Map.*;
import java.util.jar.*;
import javax.annotation.*;

import mockit.coverage.*;
import mockit.internal.util.*;

/**
 * Coverage data captured for all source files exercised during a test run.
 */
public final class CoverageData implements Serializable
{
   private static final long serialVersionUID = -4860004226098360259L;
   @Nonnull private static final CoverageData instance = new CoverageData();

   @Nonnull public static CoverageData instance() { return instance; }

   private boolean withCallPoints;

   @Nonnull private final Map<String, FileCoverageData> fileToFileData = new LinkedHashMap<>();
   @Nonnull private final List<FileCoverageData> indexedFileData = new ArrayList<>(100);

   public boolean isWithCallPoints() { return withCallPoints; }
   public void setWithCallPoints(boolean withCallPoints) { this.withCallPoints = withCallPoints; }

   @Nonnull public Map<String, FileCoverageData> getFileToFileData() { return fileToFileData; }

   @Nonnull
   public FileCoverageData getOrAddFile(@Nonnull String file, @Nullable String kindOfTopLevelType) {
      FileCoverageData fileData = fileToFileData.get(file);

      // For a class with nested/inner classes, a previous class in the same source file may already have been added.
      if (fileData == null) {
         int fileIndex = indexedFileData.size();
         fileData = new FileCoverageData(fileIndex, kindOfTopLevelType);
         indexedFileData.add(fileData);
         fileToFileData.put(file, fileData);
      }
      else if (kindOfTopLevelType != null) {
         fileData.kindOfTopLevelType = kindOfTopLevelType;
      }

      return fileData;
   }

   @Nonnull public FileCoverageData getFileData(@Nonnull String file) { return fileToFileData.get(file); }
   @Nonnull public FileCoverageData getFileData(@Nonnegative int fileIndex) { return indexedFileData.get(fileIndex); }

   public boolean isEmpty() { return fileToFileData.isEmpty(); }
   public void clear() { fileToFileData.clear(); }

   /**
    * Computes the coverage percentage over a subset of the available source files.
    *
    * @param fileNamePrefix a regular expression for matching the names of the source files to be considered, or <code>null</code> to consider
    *                       <em>all</em> files
    *
    * @return the computed percentage from <code>0</code> to <code>100</code> (inclusive), or <code>-1</code> if no meaningful value could be computed
    */
   public int getPercentage(@Nullable String fileNamePrefix) {
      int coveredItems = 0;
      int totalItems = 0;

      for (Entry<String, FileCoverageData> fileAndFileData : fileToFileData.entrySet()) {
         String sourceFile = fileAndFileData.getKey();

         if (fileNamePrefix == null || sourceFile.startsWith(fileNamePrefix)) {
            FileCoverageData fileData = fileAndFileData.getValue();
            coveredItems += fileData.getCoveredItems();
            totalItems += fileData.getTotalItems();
         }
      }

      return CoveragePercentage.calculate(coveredItems, totalItems);
   }

   /**
    * Finds the source file with the smallest coverage percentage.
    *
    * @return the percentage value for the file found, or <code>Integer.MAX_VALUE</code> if no file is found with a meaningful percentage
    */
   @Nonnegative
   public int getSmallestPerFilePercentage() {
      int minPercentage = Integer.MAX_VALUE;

      for (FileCoverageData fileData : fileToFileData.values()) {
         if (!fileData.wasLoadedAfterTestCompletion()) {
            int percentage = fileData.getCoveragePercentage();

            if (percentage >= 0 && percentage < minPercentage) {
               minPercentage = percentage;
            }
         }
      }

      return minPercentage;
   }

   public void fillLastModifiedTimesForAllClassFiles() {
      for (Iterator<Entry<String, FileCoverageData>> itr = fileToFileData.entrySet().iterator(); itr.hasNext(); ) {
         Entry<String, FileCoverageData> fileAndFileData = itr.next();
         long lastModified = getLastModifiedTimeForClassFile(fileAndFileData.getKey());

         if (lastModified > 0L) {
            FileCoverageData fileCoverageData = fileAndFileData.getValue();
            fileCoverageData.lastModified = lastModified;
            continue;
         }

         itr.remove();
      }
   }

   private long getLastModifiedTimeForClassFile(@Nonnull String sourceFilePath) {
      String sourceFilePathNoExt = sourceFilePath.substring(0, sourceFilePath.lastIndexOf('.'));
      String className = sourceFilePathNoExt.replace('/', '.');

      Class<?> coveredClass = findCoveredClass(className);

      if (coveredClass == null) {
         return 0L;
      }

      String locationPath = Utilities.getClassFileLocationPath(coveredClass);

      if (locationPath.endsWith(".jar")) {
         try { return getLastModifiedTimeFromJarEntry(sourceFilePathNoExt, locationPath); }
         catch (IOException ignore) { return 0L; }
      }

      String pathToClassFile = locationPath + sourceFilePathNoExt + ".class";

      return new File(pathToClassFile).lastModified();
   }

   private static long getLastModifiedTimeFromJarEntry(
      @Nonnull String sourceFilePathNoExt, @Nonnull String locationPath
   ) throws IOException {

      try (JarFile jarFile = new JarFile(locationPath)) {
         JarEntry classEntry = jarFile.getJarEntry(sourceFilePathNoExt + ".class");
         return classEntry.getTime();
      }
   }

   @Nullable
   private Class<?> findCoveredClass(@Nonnull String className) {
      ClassLoader currentCL = getClass().getClassLoader();
      Class<?> coveredClass = loadClass(className, currentCL);

      if (coveredClass == null) {
         ClassLoader systemCL = ClassLoader.getSystemClassLoader();

         if (systemCL != currentCL) {
            coveredClass = loadClass(className, systemCL);
         }

         if (coveredClass == null) {
            ClassLoader contextCL = Thread.currentThread().getContextClassLoader();

            if (contextCL != null && contextCL != systemCL) {
               coveredClass = loadClass(className, contextCL);
            }
         }
      }

      return coveredClass;
   }

   @Nullable
   private static Class<?> loadClass(@Nonnull String className, @Nullable ClassLoader loader) {
      try {
         return Class.forName(className, false, loader);
      }
      catch (ClassNotFoundException | NoClassDefFoundError ignore) { return null; }
   }

   /**
    * Reads a serialized <code>CoverageData</code> object from the given file (normally, a "<code>coverage.ser</code>" file generated at the end of
    * a previous test run).
    *
    * @param dataFile the ".ser" file containing a serialized <code>CoverageData</code> instance
    *
    * @return a new object containing all coverage data resulting from a previous test run
    */
   @Nonnull
   public static CoverageData readDataFromFile(@Nonnull File dataFile) throws IOException {
      try (ObjectInputStream input = new ObjectInputStream(new BufferedInputStream(new FileInputStream(dataFile)))) {
         return (CoverageData) input.readObject();
      }
      catch (ClassNotFoundException e) {
         throw new RuntimeException("Serialized class in coverage data file \"" + dataFile + "\" not found in classpath", e);
      }
   }

   public void writeDataToFile(@Nonnull File dataFile) throws IOException {
      try (ObjectOutputStream output = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(dataFile)))) {
         output.writeObject(this);
      }
   }

   public void merge(@Nonnull CoverageData previousData) {
      withCallPoints |= previousData.withCallPoints;

      for (Entry<String, FileCoverageData> previousFileAndFileData : previousData.fileToFileData.entrySet()) {
         String previousFile = previousFileAndFileData.getKey();
         FileCoverageData previousFileData = previousFileAndFileData.getValue();
         FileCoverageData fileData = fileToFileData.get(previousFile);

         if (fileData == null) {
            fileToFileData.put(previousFile, previousFileData);
         }
         else if (fileData.lastModified > 0 && previousFileData.lastModified == fileData.lastModified) {
            fileData.mergeWithDataFromPreviousTestRun(previousFileData);
         }
      }
   }
}