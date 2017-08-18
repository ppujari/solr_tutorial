/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.solr.handler;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrException.ErrorCode;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.MapSolrParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.params.UpdateParams;
import org.apache.solr.common.util.ContentStream;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.handler.loader.CSVLoader;
import org.apache.solr.handler.loader.ContentStreamLoader;
import org.apache.solr.handler.loader.JavabinLoader;
import org.apache.solr.handler.loader.JsonLoader;
import org.apache.solr.handler.loader.XMLLoader;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.update.processor.UpdateRequestProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * UpdateHandler that uses content-type to pick the right Loader
 */
public class UpdateRequestHandler extends ContentStreamHandlerBase {
  public static Logger log = LoggerFactory.getLogger(UpdateRequestHandler.class);

  // XML Constants
  public static final String ADD = "add";
  public static final String DELETE = "delete";
  public static final String OPTIMIZE = "optimize";
  public static final String COMMIT = "commit";
  public static final String ROLLBACK = "rollback";
  public static final String WAIT_SEARCHER = "waitSearcher";
  public static final String SOFT_COMMIT = "softCommit";

  public static final String OVERWRITE = "overwrite";
  
  public static final String VERSION = "version";
  
  // NOTE: This constant is for use with the <add> XML tag, not the HTTP param with same name
  public static final String COMMIT_WITHIN = "commitWithin";

  Map<String,ContentStreamLoader> loaders = null;

  ContentStreamLoader instance = new ContentStreamLoader() {
    @Override
    public void load(SolrQueryRequest req, SolrQueryResponse rsp,
        ContentStream stream, UpdateRequestProcessor processor) throws Exception {
      
      String type = req.getParams().get(UpdateParams.ASSUME_CONTENT_TYPE);
      if(type == null) {
        type = stream.getContentType();
      }
      if( type == null ) { // Normal requests will not get here.
        throw new SolrException(ErrorCode.BAD_REQUEST, "Missing ContentType");
      }
      int idx = type.indexOf(';');
      if(idx>0) {
        type = type.substring(0,idx);
      }
      ContentStreamLoader loader = loaders.get(type);
      if(loader==null) {
        throw new SolrException(ErrorCode.BAD_REQUEST, "Unsupported ContentType: "
            +type+ "  Not in: "+loaders.keySet());
      }
      if(loader.getDefaultWT()!=null) {
        setDefaultWT(req,loader);
      }
      loader.load(req, rsp, stream, processor);
    }
    
    private void setDefaultWT(SolrQueryRequest req, ContentStreamLoader loader) {
      SolrParams params = req.getParams();
      if( params.get(CommonParams.WT) == null ) {
        String wt = loader.getDefaultWT();
        // Make sure it is a valid writer
        if(req.getCore().getQueryResponseWriter(wt)!=null) {
          Map<String,String> map = new HashMap<String,String>(1);
          map.put(CommonParams.WT, wt);
          req.setParams(SolrParams.wrapDefaults(params, 
              new MapSolrParams(map)));
        }
      }
    }
  };
  
  @Override
  public void init(NamedList args) {
    super.init(args);
    
    // Since backed by a non-thread safe Map, it should not be modifiable
    loaders = Collections.unmodifiableMap(createDefaultLoaders(args));
  }
  
  protected void setAssumeContentType(String ct) {
    if(invariants==null) {
      Map<String,String> map = new HashMap<String,String>();
      map.put(UpdateParams.ASSUME_CONTENT_TYPE,ct);
      invariants = new MapSolrParams(map);
    }
    else {
      ModifiableSolrParams params = new ModifiableSolrParams(invariants);
      params.set(UpdateParams.ASSUME_CONTENT_TYPE,ct);
      invariants = params;
    }
  }
  
  protected Map<String,ContentStreamLoader> createDefaultLoaders(NamedList args) {
    SolrParams p = null;
    if(args!=null) {
      p = SolrParams.toSolrParams(args);
    }
    Map<String,ContentStreamLoader> registry = new HashMap<String,ContentStreamLoader>();
    registry.put("application/xml", new XMLLoader().init(p) );
    registry.put("application/json", new JsonLoader().init(p) );
    registry.put("application/csv", new CSVLoader().init(p) );
    registry.put("application/javabin", new JavabinLoader().init(p) );
    registry.put("text/csv", registry.get("application/csv") );
    registry.put("text/xml", registry.get("application/xml") );
    registry.put("text/json", registry.get("application/json") );
    return registry;
  }
  

  @Override
  protected ContentStreamLoader newLoader(SolrQueryRequest req, final UpdateRequestProcessor processor) {
    return instance;
  }
  
  //////////////////////// SolrInfoMBeans methods //////////////////////

  @Override
  public String getDescription() {
    return "Add documents using XML (with XSLT), CSV, JSON, or javabin";
  }

  @Override
  public String getSource() {
    return "$URL: http://svn.apache.org/repos/asf/lucene/dev/branches/lucene_solr_4_0/solr/core/src/java/org/apache/solr/handler/UpdateRequestHandler.java $";
  }
}



