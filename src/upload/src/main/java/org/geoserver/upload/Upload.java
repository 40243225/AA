package org.geoserver.upload;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.upload.FileUpload;
import org.apache.wicket.markup.html.form.upload.FileUploadField;
import org.apache.wicket.markup.html.form.upload.MultiFileUploadField;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.util.file.Files;
import org.geoserver.web.GeoServerSecuredPage;
import org.geoserver.web.ComponentAuthorizer;

public class Upload extends GeoServerSecuredPage {

	private static final String TARGET_PATH = "C:\\Program Files\\Apache Software Foundation\\Tomcat 8.5\\webapps\\Cloud-based GIS\\data\\使用者上傳區";

	private static final long serialVersionUID = 1L;

	public Upload() {
		super();
		
		add(new UploadForm("uploadForm"));
	}
	protected ComponentAuthorizer getPageAuthorizer() {
        return ComponentAuthorizer.WORKSPACE_ADMIN;
    }

	private class UploadForm extends Form<Void> {

		//private List<FileUpload> uploads;
		private final Collection<FileUpload> uploads = new ArrayList<>();
		
		public Collection<FileUpload> getUploads()
		{
			return uploads;
		}
		
		public UploadForm(String id) {
			super(id);
			
			
			
			setMultiPart(true);
			add(new MultiFileUploadField("uploadField", new PropertyModel<List<FileUpload>>(this, "uploads"), 5, true));
		}

		@Override
		protected void onSubmit() {
			for(FileUpload upload : uploads)
			{
				String filePath = TARGET_PATH + "/" + upload.getClientFileName();
				File newFile=new File(filePath);
				checkFileExists(newFile);
				try {				
					newFile.createNewFile();
					upload.writeTo(newFile);
					info(String.format(
							"Upload successful. The file has been stored in %s",filePath));
				} catch (IOException e) {
					e.printStackTrace();
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			
		}
		private void checkFileExists(File newFile)
		{
			if (newFile.exists())
			{
				// Try to delete the file
				if (!Files.remove(newFile))
				{
					throw new IllegalStateException("Unable to overwrite " + newFile.getAbsolutePath());
				}
			}
		}
		
	}
}
