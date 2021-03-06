package org.nrg.xnat.plugins.ccdb.service;

import org.apache.commons.io.FileUtils;
import org.nrg.xdat.bean.CatCatalogBean;
import org.nrg.xdat.bean.CatEntryBean;
import org.nrg.xdat.model.XnatAbstractresourceI;
import org.nrg.xdat.model.XnatSubjectdataFieldI;
import org.nrg.xdat.om.*;
import org.nrg.xft.XFTItem;
import org.nrg.xft.event.EventMetaI;
import org.nrg.xft.event.EventUtils;
import org.nrg.xft.security.UserI;
import org.nrg.xft.utils.SaveItemHelper;
import org.nrg.xnat.helpers.uri.UriParserUtils;
import org.nrg.xnat.plugins.ccdb.rest.hotel.HandlerException;
import org.nrg.xnat.services.archive.CatalogService;
import org.nrg.xnat.utils.CatalogUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Common things done in xnat.
 */
@Service
public class XnatService {
    private final CatalogService _catalogService;
    private static final Logger _log = LoggerFactory.getLogger( "ccdbLogger");

    @Autowired
    public XnatService(CatalogService catalogService) {
        _catalogService = catalogService;
    }

    /**
     * Get existing subject or create a new one.
     *
     * @param projectdata The project data for the subject
     * @param subjectLabel The subject's label.
     * @param user The user performing this action.
     * @return The subject data for the specified subject.
     * @throws HandlerException
     */
    public XnatSubjectdata getOrCreateSubject(XnatProjectdata projectdata, String subjectLabel, UserI user) throws XnatServiceException {
        XnatSubjectdata subjectdata = XnatSubjectdata.GetSubjectByProjectIdentifier( projectdata.getProject(), subjectLabel, user, false);
        if( subjectdata == null) {
            try {
                XFTItem item = XFTItem.NewItem("xnat:subjectData", user);
                subjectdata = new XnatSubjectdata( item);
                String id = XnatSubjectdata.CreateNewID();
                subjectdata.setProject( projectdata.getId());
                subjectdata.setId( id);
                subjectdata.setLabel( subjectLabel);
                EventMetaI eventMeta = EventUtils.DEFAULT_EVENT( user, "create subject");
                subjectdata.save( user, false, false, eventMeta);
            } catch (Exception e) {
                String msg = "Error creating hotel subject: " + subjectLabel;
                throw new XnatServiceException( msg, e);
            }
        }
        return subjectdata;
    }

    public XnatImagesessiondata getOrCreateImageSession(XnatSubjectdata subjectdata,
                                                        String modality, String sessionLabel,
                                                        UserI user) throws XnatServiceException {
        XnatImagesessiondata imagesessiondata = null;

        List<XnatSubjectassessordata> subjectassessordataList = subjectdata.getExperiments_experiment("");

        List<XnatImagesessiondata> imagesessiondataList = subjectassessordataList.stream()
                .filter( sad -> sad.getLabel().matches( sessionLabel))
                .filter( sad -> sad instanceof XnatImagesessiondata)
                .map( sad -> {return (XnatImagesessiondata) sad;})
                .collect(Collectors.toList());

        if( imagesessiondataList.isEmpty()) {
            imagesessiondata = createImageSession( modality, sessionLabel, subjectdata, user);
        }
        else {
            imagesessiondata = imagesessiondataList.get(0);
        }
        return imagesessiondata;
    }

    public XnatImagesessiondata createImageSession( String modality, String sessionLabel, XnatSubjectdata subjectdata, UserI user) throws XnatServiceException {
        try {
            XnatImagesessiondata imagesessiondata;
            switch( modality) {
                case "PET":
                    imagesessiondata = new XnatPetsessiondata();
                    break;
                case "CT":
                    imagesessiondata = new XnatCtsessiondata();
                    break;
                default:
                    imagesessiondata = new XnatImagesessiondata();
            }
            imagesessiondata.setId( XnatExperimentdata.CreateNewID());
            imagesessiondata.setLabel( sessionLabel);
            imagesessiondata.setModality( modality);

            imagesessiondata.setProject( subjectdata.getProject());
            imagesessiondata.setSubjectId( subjectdata.getId());
            imagesessiondata.setDate( new Date());

            EventMetaI eventMeta = EventUtils.DEFAULT_EVENT(user, "create image session.");
            SaveItemHelper.authorizedSave(imagesessiondata.getItem(), user, false, false, false, false, eventMeta);

            return imagesessiondata;
        }
        catch( Exception e) {
            String msg = "Error creating imagesessiondata for subject: " + subjectdata.getLabel();
            throw new XnatServiceException(msg, e);
        }
    }

    public XnatImagescandata createImageScan( String imageSessionID, String modality, String id, String type, UserI user) throws XnatServiceException {
        try {
            XnatImagescandata scandata;
            switch (modality) {
                case "PET":
                    scandata = new XnatPetscandata(user);
                    break;
                case "CT":
                    scandata = new XnatCtscandata( user);
                    break;
                default:
                    scandata = new XnatImagescandata( user);
            }
            scandata.setId( id);
            scandata.setType( type);
            scandata.setImageSessionId( imageSessionID);

            EventMetaI eventMeta = EventUtils.DEFAULT_EVENT(user, "create image scan.");
            SaveItemHelper.authorizedSave( scandata.getItem(), user, false, false, false, false, eventMeta);
            return scandata;
        }
        catch( Exception e) {
            String msg = "Error creating image scan data for session: " + imageSessionID;
            throw new XnatServiceException(msg, e);
        }
    }

    public XnatResourcecatalog createScanResource(XnatImagesessiondata imagesessiondata, UserI user) throws XnatServiceException {
        try {
            final XnatResourcecatalog catalog;
            String parentURI = UriParserUtils.getArchiveUri( imagesessiondata);
            String resourceName = "microPET";
            String format = "microPET";
            String content = "microPET";
            catalog = _catalogService.createAndInsertResourceCatalog(user, parentURI, resourceName, "Creating resource catalog \"" + resourceName + "\" for microPET \"", format, content);

            return catalog;
        }
        catch( Exception e) {
            String msg = "Error creating scan resource. ";
            throw new XnatServiceException(msg, e);
        }
    }

    public XnatResourcecatalog createScanResource(XnatImagescandata imagescandata, String resourceName, String format, String content, UserI user) throws XnatServiceException {
        try {
            final XnatResourcecatalog catalog;
            _log.debug("imagescandata: {}", imagescandata);
//            String parentURI = UriParserUtils.getArchiveUri( imagescandata);
            String parentURI = String.format("/archive/experiments/%s/scans/%s/resources/%s",imagescandata.getImageSessionId(), imagescandata.getId(), resourceName);
            _log.debug("uri: {}", parentURI);
            catalog = _catalogService.createAndInsertResourceCatalog(user, parentURI, resourceName, "description" + resourceName, format, content);

            return catalog;
        }
        catch( Exception e) {
            String msg = "Error creating scan resource: " + e.getMessage();
            throw new XnatServiceException(msg, e);
        }
    }

    public XnatResourcecatalog addResources( XnatSubjectdata sd, Collection<File> resources, final boolean preserveDirectories, final String label, final String description, final String format, final String content, UserI user) throws Exception {
        String uri = UriParserUtils.getArchiveUri( sd);
        List<XnatAbstractresourceI> resources_resource = sd.getResources_resource();
        for( XnatAbstractresourceI resource: resources_resource) {
        }
        Map<String, String> params = new HashMap<>();
        String resourceURI = UriParserUtils.getArchiveUriFromParameterTypes(params);
        resourceURI = String.format("/archive/subjects/%s/resources/%s", sd.getId(), label);
        XnatResource xnatResourcesByUri = XnatResourcecatalog.getXnatResourcesByUri(resourceURI, user, false);
//        XnatResourcecatalog.getXnatAbstractresourcesByField()
        XnatResourcecatalog resourcecatalog;
        return null;
    }

    public XnatResourcecatalog insertResources( XnatSubjectdata sd, Collection<File> resources, final boolean preserveDirectories, final String label, final String description, final String format, final String content, UserI user) throws Exception {
        String uri = UriParserUtils.getArchiveUri( sd);
        return _catalogService.insertResources( user, uri, resources, preserveDirectories, label, description, format, content);
    }

    public XnatResourcecatalog insertResources(String parentUri, Collection<File> resources, final boolean preserveDirectories, final String label, final String description, final String format, final String content, UserI user) throws Exception {
        return _catalogService.insertResources( user, parentUri, resources, preserveDirectories, label, description, format, content);
    }

    /*
     * Ugh.  Why so hard?  entries are in catalog file but Manage Files doesn't see them.
     */
    public XnatResourcecatalog insertResource( XnatResourcecatalog catalog, String rootPath, final File resource, final boolean preserveDirectories, final String format, final String content, final UserI user) throws Exception {

        String fileName = resource.getName();
        final File destination = new File(catalog.getUri()).getParentFile();
        if (resource.isDirectory() && preserveDirectories) {
            FileUtils.copyDirectoryToDirectory(resource, destination);
        } else if (resource.isDirectory() && !preserveDirectories) {
            FileUtils.copyDirectory(resource, destination);
        } else {
            FileUtils.copyFileToDirectory(resource, destination);
        }

        CatCatalogBean catCatalogBean = CatalogUtils.getCatalog(rootPath, catalog);

        Path destinationFilepath = destination.toPath().resolve( fileName);
        _log.debug("destinationFilepath: {}", destinationFilepath);
        CatEntryBean catEntry = new CatEntryBean();
        catEntry.setId( String.format("%s", fileName));
        catEntry.setName( fileName);
        catEntry.setFormat( format);
        catEntry.setContent( content);
        catEntry.setUri( fileName);
        catCatalogBean.addEntries_entry( catEntry);

        CatalogUtils.writeCatalogToFile( catCatalogBean, new File(catalog.getUri()), false);
        EventMetaI now = EventUtils.DEFAULT_EVENT(user, "insert resource.");

//        catCatalogBean.save(user, false, false, now);

        return catalog;
    }


//    public XnatImagescandata createImageScan(String scanLabel, List<ResourceFile> files) throws XnatServiceException {
//        try {
//            XnatImagescandata imagescandata = new XnatImagescandata();
//            XnatCtscandata ctscandata = new XnatCtscandata();
//            imagescandata.setId( "1");
//            imagescandata.addFile( files.get(0));
//            ctscandata.addFile( files.get(0));
//
//
//            EventMetaI eventMeta = EventUtils.DEFAULT_EVENT(user, "create image session.");
//            SaveItemHelper.authorizedSave(imagesessiondata.getItem(), user, false, false, false, false, eventMeta);
//
//            return imagescandata;
//        }
//        catch( Exception e) {
//            String msg = "Error creating imagesessiondata for subject: " + scanLabel;
//            throw new XnatServiceException(msg, e);
//        }
//    }

    public void insertOrUpdateField( XnatSubjectdata subjectdata, String key, String value, UserI user) throws XnatServiceException {
        List<XnatSubjectdataFieldI> fields = subjectdata.getFields_field();
        XnatSubjectdataField field;
        try {
            Optional<XnatSubjectdataFieldI> f = fields.stream().filter( i -> i.getName().equals( key)).findAny();
            if( f.isPresent()) {
                if( ! f.get().getField().equals(value)) {
                    f.get().setField(value);
                }
            } else {
                XnatSubjectdataField newField = new XnatSubjectdataField(user);
                newField.setName(key);
                newField.setField(value);
                subjectdata.addFields_field(newField);
            }
            EventMetaI eventMeta = EventUtils.DEFAULT_EVENT(user, "update data field on subject.");
            SaveItemHelper.authorizedSave(subjectdata.getItem(), user, false, false, false, false, eventMeta);
        }
        catch( Exception e) {
            String msg = String.format("Error editing custom field (%s=%s) on subject '%s'.", key, value, subjectdata.getId());
            throw new XnatServiceException(msg, e);
        }
    }

    public XnatImagesessiondata getImageSession( String sessionID, UserI user) {
        XnatImagesessiondata isd = XnatImagesessiondata.getXnatImagesessiondatasById( sessionID, user, false);
        return isd;
    }
}
