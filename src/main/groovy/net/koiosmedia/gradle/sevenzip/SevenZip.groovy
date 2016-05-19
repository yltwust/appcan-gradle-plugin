package net.koiosmedia.gradle.sevenzip

import org.gradle.api.Project
import org.gradle.api.internal.file.copy.CopyAction
import org.gradle.api.internal.file.copy.CopyActionProcessingStream
import org.gradle.api.internal.tasks.SimpleWorkResult
import org.gradle.api.tasks.AbstractCopyTask
import org.gradle.api.tasks.WorkResult
import org.gradle.api.tasks.bundling.AbstractArchiveTask

class SevenZip extends AbstractArchiveTask {


    public List<File> mFiles;

	public class SevenZipCopyAction implements CopyAction {
		private final File archiveFile;

        private Project project
		public SevenZipCopyAction(Project project, final File archiveFile) {
            this.project=project
			this.archiveFile=archiveFile;
		}
		
		@Override
		public WorkResult execute(final CopyActionProcessingStream stream) {
            com.swemel.sevenzip.SevenZip sevenZip =
				new com.swemel.sevenzip.SevenZip(archiveFile.getAbsolutePath(),toFileArray(mFiles));
			sevenZip.createArchive();
			return new SimpleWorkResult(true);
		}
		

	}

    private static File[] toFileArray(List<File> files) {
        File[] fileArray=new File[files.size()];

        for(int i=0; i<files.size(); i++) {
            fileArray[i]=files.get(i);
        }

        return fileArray;
    }

    @Override
    AbstractCopyTask from(Object... sourcePaths) {
        if (mFiles==null){
            mFiles=new ArrayList<File>()
        }
        if (sourcePaths!=null){
            sourcePaths.each { path ->
                mFiles.add(project.file(path))
            }
        }
        println("sourcePaths: "+mFiles)
        return super.from(sourcePaths)
    }


    public static final String SEVENZ_EXTENSION = "7z";
	
	public SevenZip() {
		setExtension(SEVENZ_EXTENSION);
	}
	
	@Override
	protected CopyAction createCopyAction() {
		return new SevenZipCopyAction(project,getArchivePath());
	}
}
