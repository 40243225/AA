/* (c) 2014 - 2016 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.wms.web.data;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.apache.commons.io.IOUtils;
import org.apache.wicket.Component;
import org.apache.wicket.MarkupContainer;
import org.apache.wicket.Session;
import org.apache.wicket.WicketRuntimeException;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.attributes.AjaxCallListener;
import org.apache.wicket.ajax.attributes.AjaxRequestAttributes;
import org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior;
import org.apache.wicket.ajax.markup.html.form.AjaxSubmitLink;
import org.apache.wicket.core.util.string.JavaScriptUtils;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.form.ChoiceRenderer;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.form.upload.FileUpload;
import org.apache.wicket.markup.html.form.upload.FileUploadField;
import org.apache.wicket.markup.html.image.Image;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.request.resource.AbstractResource;
import org.apache.wicket.util.lang.Bytes;
import org.geoserver.catalog.ResourcePool;
import org.geoserver.catalog.StyleGenerator;
import org.geoserver.catalog.StyleHandler;
import org.geoserver.catalog.StyleInfo;
import org.geoserver.catalog.StyleType;
import org.geoserver.catalog.Styles;
import org.geoserver.catalog.WorkspaceInfo;
import org.geoserver.catalog.impl.StyleInfoImpl;
import org.geoserver.config.GeoServerDataDirectory;
import org.geoserver.ows.util.ResponseUtils;
import org.geoserver.platform.GeoServerExtensions;
import org.geoserver.platform.resource.Resource;
import org.geoserver.web.ComponentAuthorizer;
import org.geoserver.web.GeoServerSecuredPage;
import org.geoserver.web.data.style.StyleDetachableModel;
import org.geoserver.web.data.workspace.WorkspaceChoiceRenderer;
import org.geoserver.web.data.workspace.WorkspacesModel;
import org.geoserver.web.wicket.CodeMirrorEditor;
import org.geoserver.web.wicket.GeoServerAjaxFormLink;
import org.geoserver.web.wicket.ParamResourceModel;
import org.geoserver.wms.GetLegendGraphicRequest;
import org.geoserver.wms.legendgraphic.BufferedImageLegendGraphicBuilder;
import org.geoserver.wms.web.publish.StyleChoiceRenderer;
import org.geoserver.wms.web.publish.StyleTypeChoiceRenderer;
import org.geoserver.wms.web.publish.StyleTypeModel;
import org.geoserver.wms.web.publish.StylesModel;
import org.geotools.styling.Style;
import org.xml.sax.SAXParseException;

/**
 * Base page for creating/editing styles
 */
@SuppressWarnings("serial")
public abstract class AbstractStylePage extends GeoServerSecuredPage {

    protected String format;

    protected TextField nameTextField;

    protected FileUploadField fileUploadField;

    protected DropDownChoice templates;

    protected AjaxSubmitLink generateLink;

    protected DropDownChoice styles;

    protected AjaxSubmitLink copyLink;

    protected Form uploadForm;

    protected Form generateForm;

    protected Form styleForm;

    protected CodeMirrorEditor editor;

    protected MarkupContainer formatReadOnlyMessage;

    String rawStyle;

    private Image legendImg;

    String lastStyle;

    private WebMarkupContainer legendContainer;

    private DropDownChoice<WorkspaceInfo> wsChoice;
    DropDownChoice<String> formatChoice;

    public AbstractStylePage() {
    }

    public AbstractStylePage(StyleInfo style) {
        initUI(style);
    }

    protected void initUI(StyleInfo style) {
        CompoundPropertyModel<StyleInfo> styleModel = new CompoundPropertyModel(style != null ? 
            new StyleDetachableModel(style) : getCatalog().getFactory().createStyle());
        
        format = style != null ? style.getFormat() : getCatalog().getFactory().createStyle()
                .getFormat();

        // Make sure the legend object isn't null
        if (null == styleModel.getObject().getLegend()) {
            styleModel.getObject().setLegend(getCatalog().getFactory().createLegend());
        }

        styleForm = new Form("form", styleModel) {
            @Override
            protected void onSubmit() {
                super.onSubmit();
                onStyleFormSubmit();
            }
        };
        styleForm.setMarkupId("mainForm");
        add(styleForm);

        styleForm.add(nameTextField = new TextField("name"));
        nameTextField.setRequired(true);
        
        wsChoice = 
            new DropDownChoice<WorkspaceInfo>("workspace", new WorkspacesModel(), new WorkspaceChoiceRenderer());
        wsChoice.setNullValid(true);
        if (!isAuthenticatedAsAdmin()) {
            wsChoice.setNullValid(false);
            wsChoice.setRequired(true);
        }

        styleForm.add(wsChoice);

        formatChoice = new DropDownChoice<String>("format", new PropertyModel(this, "format"),
                new StyleFormatsModel(), new ChoiceRenderer<String>() {
            @Override
            public String getIdValue(String object, int index) {
                return object;
            }

            @Override
            public Object getDisplayValue(String object) {
                return Styles.handler(object).getName();
            }
        });
        formatChoice.add(new AjaxFormComponentUpdatingBehavior("change") {
            @Override
            protected void onUpdate(AjaxRequestTarget target) {
                target.appendJavaScript(String.format(
                    "if (document.gsEditors) { document.gsEditors.editor.setOption('mode', '%s'); }", styleHandler().getCodeMirrorEditMode()));
            }
        });
        styleForm.add(formatChoice);

        formatReadOnlyMessage = new WebMarkupContainer("formatReadOnly", new Model());
        formatReadOnlyMessage.setVisible(false);
        styleForm.add(formatReadOnlyMessage);

        styleForm.add(editor = new CodeMirrorEditor("styleEditor", styleHandler()
                .getCodeMirrorEditMode(), new PropertyModel(this, "rawStyle")));
        // force the id otherwise this blasted thing won't be usable from other forms
        editor.setTextAreaMarkupId("editor");
        editor.setOutputMarkupId(true);
        editor.setRequired(true);
        styleForm.add(editor);

        // add the Legend fields        
        ExternalGraphicPanel legendPanel = new ExternalGraphicPanel("legendPanel", styleModel, styleForm);
        legendPanel.setOutputMarkupId(true);
        styleForm.add(legendPanel);

        if (style != null) {
            try {
                setRawStyle(readFile(style));
            } catch (IOException e) {
                // ouch, the style file is gone! Register a generic error message
                Session.get().error(new ParamResourceModel("styleNotFound", this, style.getFilename()).getString());
            }
        }
        
        // style generation functionality
        templates = new DropDownChoice("templates", new Model(), new StyleTypeModel(), new StyleTypeChoiceRenderer());
        templates.setOutputMarkupId(true);
        templates.add(new AjaxFormComponentUpdatingBehavior("change") {

            @Override
            protected void onUpdate(AjaxRequestTarget target) {
                templates.validate();
                generateLink.setEnabled(templates.getConvertedInput() != null);
                target.add(generateLink);
            }
        });
        styleForm.add(templates);
        generateLink = generateLink();
        generateLink.setEnabled(false);
        styleForm.add(generateLink);

        // style copy functionality
        styles = new DropDownChoice("existingStyles", new Model(), new StylesModel(), new StyleChoiceRenderer());
        styles.setOutputMarkupId(true);
        styles.add(new AjaxFormComponentUpdatingBehavior("change") {

            @Override
            protected void onUpdate(AjaxRequestTarget target) {
                styles.validate();
                copyLink.setEnabled(styles.getConvertedInput() != null);
                target.add(copyLink);
            }
        });
        styleForm.add(styles);
        copyLink = copyLink();
        copyLink.setEnabled(false);
        styleForm.add(copyLink);

        uploadForm = uploadForm(styleForm);
        uploadForm.setMultiPart(true);
        uploadForm.setMaxSize(Bytes.megabytes(1));
        uploadForm.setMarkupId("uploadForm");
        add(uploadForm);

        uploadForm.add(fileUploadField = new FileUploadField("filename"));

        

        add(validateLink());
        add(previewLink());
        Link cancelLink = new Link("cancel") {
            @Override
            public void onClick() {
                doReturn(StylePage.class);
            }
        };
        add(cancelLink);

        legendContainer = new WebMarkupContainer("legendContainer");
        legendContainer.setOutputMarkupId(true);
        add(legendContainer);
        this.legendImg = new Image("legendImg", new AbstractResource() {
            
            @Override
            protected ResourceResponse newResourceResponse(Attributes attributes) {
                ResourceResponse rr = new ResourceResponse();
                rr.setContentType("image/png");
                rr.setWriteCallback(new WriteCallback() {
                    
                    @Override
                    public void writeData(Attributes attributes) throws IOException {
                        GeoServerDataDirectory dd = GeoServerExtensions.bean(GeoServerDataDirectory.class, getGeoServerApplication().getApplicationContext());
                        StyleInfo si = new StyleInfoImpl(getCatalog());
                        String styleName = "tmp" + UUID.randomUUID().toString();
                        String styleFileName =  styleName + ".sld";
                        si.setFilename(styleFileName);
                        si.setName(styleName);
                        si.setWorkspace(wsChoice.getModel().getObject());
                        Resource styleResource = null;
                        try {
                            styleResource = dd.style(si);
                            try(OutputStream os = styleResource.out()) {
                                IOUtils.write(lastStyle, os);
                            }
                            Style style = dd.parsedStyle(si);
                            if (style != null) {
                                GetLegendGraphicRequest request = new GetLegendGraphicRequest();
                                request.setLayer(null);
                                request.setStyle(style);
                                request.setStrict(false);
                                Map<String, String> legendOptions = new HashMap<String, String>();
                                legendOptions.put("forceLabels", "on");
                                legendOptions.put("fontAntiAliasing", "true");
                                request.setLegendOptions(legendOptions);
                                BufferedImageLegendGraphicBuilder builder = new BufferedImageLegendGraphicBuilder();
                                BufferedImage image = builder.buildLegendGraphic(request);

                                ByteArrayOutputStream bos = new ByteArrayOutputStream();

                                ImageIO.write(image, "PNG", attributes.getResponse().getOutputStream());
                            }

                            error("Failed to build legend preview");
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        } finally {
                            if(styleResource != null) {
                                styleResource.delete();
                            }
                        }
                        
                    }
                });
                return rr;
            }});
        legendContainer.add(this.legendImg);
        this.legendImg.setVisible(false);
        this.legendImg.setOutputMarkupId(true);
    }

    StyleHandler styleHandler() {
        String modelObject = formatChoice.getModelObject();
        return Styles.handler(modelObject);
    }

    Form uploadForm(final Form form) {
        return new Form("uploadForm") {
            @Override
            protected void onSubmit() {
                FileUpload upload = fileUploadField.getFileUpload();
                if (upload == null) {
                    warn("No file selected.");
                    return;
                }
                ByteArrayOutputStream bout = new ByteArrayOutputStream();

                try {
                    IOUtils.copy(upload.getInputStream(), bout);
                    setRawStyle(new InputStreamReader(new ByteArrayInputStream(bout.toByteArray()), "UTF-8"));
                    editor.setModelObject(rawStyle);
                } catch (IOException e) {
                    throw new WicketRuntimeException(e);
                }

                // update the style object
                StyleInfo s = (StyleInfo) form.getModelObject();
                if (s.getName() == null || "".equals(s.getName().trim())) {
                    // set it
                    nameTextField.setModelValue(new String[] {ResponseUtils.stripExtension(upload
                            .getClientFileName())});
                    nameTextField.modelChanged();
                }
            }
        };
    }
    
    Component validateLink() {
        return new GeoServerAjaxFormLink("validate", styleForm) {
            
            @Override
            protected void onClick(AjaxRequestTarget target, Form form) {
                editor.processInput();

                List<Exception> errors = validateSLD();
                
                if ( errors.isEmpty() ) {
                    form.info( "No validation errors.");
                } else {
                    for( Exception e : errors ) {
                        form.error( sldErrorWithLineNo(e) );
                    }    
                }        
            }
            
            @Override
            protected void updateAjaxAttributes(AjaxRequestAttributes attributes) {
                super.updateAjaxAttributes(attributes);
                attributes.getAjaxCallListeners().add(editor.getSaveDecorator());
            }
        };
    }
    
    Component previewLink() {
        return new GeoServerAjaxFormLink("preview", styleForm) {

            @Override
            protected void onClick(AjaxRequestTarget target, Form form) {
                editor.processInput();
                wsChoice.processInput();
                lastStyle = editor.getInput();

                legendImg.setVisible(true);
                target.add(legendContainer);
            }

            @Override
            protected void updateAjaxAttributes(AjaxRequestAttributes attributes) {
                super.updateAjaxAttributes(attributes);
                attributes.getAjaxCallListeners().add(editor.getSaveDecorator());
            }
        };
    }

    private String sldErrorWithLineNo(Exception e) {
        if (e instanceof SAXParseException) {
            SAXParseException se = (SAXParseException) e;
            return "line " + se.getLineNumber() + ": " + e.getLocalizedMessage();
        }
        String message = e.getLocalizedMessage();
        if(message != null) {
            return message;
        } else {
            return new ParamResourceModel("genericError", this).getString();
        }
    }
    
    List<Exception> validateSLD() {
        try {
            final String sld = editor.getInput();
            ByteArrayInputStream input = new ByteArrayInputStream(sld.getBytes());
            List<Exception> validationErrors = styleHandler().validate(input, null, getCatalog().getResourcePool().getEntityResolver());
            return validationErrors;
        } catch( Exception e ) {
            return Arrays.asList( e );
        }
    }

    AjaxSubmitLink generateLink() {
        return new AjaxSubmitLink("generate") {

            @Override
            protected void onSubmit(AjaxRequestTarget target, Form form) {
                // we need to force validation or the value won't be converted
                templates.processInput();
                StyleType template = (StyleType) templates.getConvertedInput();
                StyleGenerator styleGen = new StyleGenerator(getCatalog());
                styleGen.setWorkspace(wsChoice.getModel().getObject());

                if (template != null) {
                    try {
                        // same here, force validation or the field won't be updated
                        editor.reset();
                        setRawStyle(new StringReader(styleGen.generateStyle(styleHandler(), template, nameTextField.getInput())));
                        target.appendJavaScript(String
                                .format("if (document.gsEditors) { document.gsEditors.editor.setOption('mode', '%s'); }", styleHandler().getCodeMirrorEditMode()));

                    } catch (Exception e) {
                        error("Errors occurred generating the style");
                    }
                    target.add(styleForm);
                }
            }
            
            @Override
            protected void updateAjaxAttributes(AjaxRequestAttributes attributes) {
                super.updateAjaxAttributes(attributes);
                attributes.getAjaxCallListeners().add(new AjaxCallListener() {
                    @Override
                    public CharSequence getPrecondition(Component component) {
                        CharSequence message = new ParamResourceModel("confirmOverwrite", AbstractStylePage.this)
                                .getString();
                        message = JavaScriptUtils.escapeQuotes(message);
                        return "var val = attrs.event.view.document.gsEditors ? "
                                + "attrs.event.view.document.gsEditors." + editor.getTextAreaMarkupId() + ".getValue() : "
                                + "attrs.event.view.document.getElementById(\"" + editor.getTextAreaMarkupId() + "\").value; "
                                + "if(val != '' &&"
                                + "!confirm('"
                                + message + "')) return false;";
                    }
                });
            }

            @Override
            public boolean getDefaultFormProcessing() {
                return false;
            }

        };
    }

    AjaxSubmitLink copyLink() {
        return new AjaxSubmitLink("copy") {

            @Override
            protected void onSubmit(AjaxRequestTarget target, Form form) {
                // we need to force validation or the value won't be converted
                styles.processInput();
                StyleInfo style = (StyleInfo) styles.getConvertedInput();

                if (style != null) {
                    try {
                        // same here, force validation or the field won't be udpated
                        editor.reset();
                        setRawStyle(readFile(style));
                        formatChoice.setModelObject(style.getFormat());
                        target.appendJavaScript(String
                                .format("if (document.gsEditors) { document.gsEditors.editor.setOption('mode', '%s'); }", styleHandler().getCodeMirrorEditMode()));

                    } catch (Exception e) {
                        error("Errors occurred loading the '" + style.getName() + "' style");
                    }
                    target.add(styleForm);
                }
            }
            
            @Override
            protected void updateAjaxAttributes(AjaxRequestAttributes attributes) {
                super.updateAjaxAttributes(attributes);
                attributes.getAjaxCallListeners().add(new AjaxCallListener() {
                    @Override
                    public CharSequence getPrecondition(Component component) {
                        CharSequence message = new ParamResourceModel("confirmOverwrite", AbstractStylePage.this)
                                .getString();
                        message = JavaScriptUtils.escapeQuotes(message);
                        return "var val = attrs.event.view.document.gsEditors ? "
                                + "attrs.event.view.document.gsEditors." + editor.getTextAreaMarkupId() + ".getValue() : "
                                + "attrs.event.view.document.getElementById(\"" + editor.getTextAreaMarkupId() + "\").value; "
                                + "if(val != '' &&"
                                + "!confirm('"
                                + message + "')) return false;";
                    }
                });
            }

            @Override
            public boolean getDefaultFormProcessing() {
                return false;
            }

        };
    }

    Reader readFile(StyleInfo style) throws IOException {
        ResourcePool pool = getCatalog().getResourcePool();
        return pool.readStyle(style);
    }
    
    public void setRawStyle(Reader in) throws IOException {
        BufferedReader bin = null;
        if ( in instanceof BufferedReader ) {
            bin = (BufferedReader) in;
        }
        else {
            bin = new BufferedReader( in );
        }
        
        StringBuilder builder = new StringBuilder();
        String line = null;
        while ((line = bin.readLine()) != null ) {
            builder.append(line).append("\n");
        }

        this.rawStyle = builder.toString();
        editor.setModelObject(rawStyle);
        in.close();
    }

    /**
     * Subclasses must implement to define the submit behavior
     */
    protected abstract void onStyleFormSubmit();

    @Override
    protected ComponentAuthorizer getPageAuthorizer() {
        return ComponentAuthorizer.WORKSPACE_ADMIN;
    }
}