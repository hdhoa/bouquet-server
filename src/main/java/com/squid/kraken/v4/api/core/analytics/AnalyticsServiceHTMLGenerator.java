/*******************************************************************************
 * Copyright © Squid Solutions, 2016
 *
 * This file is part of Open Bouquet software.
 *  
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation (version 3 of the License).
 *
 * There is a special FOSS exception to the terms and conditions of the 
 * licenses as they are applied to this program. See LICENSE.txt in
 * the directory of this program distribution.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Squid Solutions also offers commercial licenses with additional warranties,
 * professional functionalities or services. If you purchase a commercial
 * license, then it supersedes and replaces any other agreement between
 * you and Squid Solutions (above licenses and LICENSE.txt included).
 * See http://www.squidsolutions.com/EnterpriseBouquet/
 *******************************************************************************/
package com.squid.kraken.v4.api.core.analytics;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.Map.Entry;

import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriBuilderException;

import org.apache.commons.lang3.StringEscapeUtils;
import org.eclipse.xtext.ide.editor.contentassist.ContentAssistEntry;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.squid.core.domain.IDomain;
import com.squid.core.domain.operators.ListContentAssistEntry;
import com.squid.core.domain.operators.OperatorDefinition;
import com.squid.core.domain.operators.OperatorScope;
import com.squid.core.expression.ExpressionAST;
import com.squid.core.expression.scope.ScopeException;
import com.squid.kraken.v4.api.core.APIException;
import com.squid.kraken.v4.api.core.ServiceUtils;
import com.squid.kraken.v4.core.analysis.engine.hierarchy.DimensionIndex;
import com.squid.kraken.v4.core.analysis.scope.AxisExpression;
import com.squid.kraken.v4.core.analysis.universe.Axis;
import com.squid.kraken.v4.core.analysis.universe.Measure;
import com.squid.kraken.v4.core.analysis.universe.Space;
import com.squid.kraken.v4.core.expression.scope.ExpressionSuggestionHandler;
import com.squid.kraken.v4.model.AnalyticsQuery;
import com.squid.kraken.v4.model.Bookmark;
import com.squid.kraken.v4.model.DataTable;
import com.squid.kraken.v4.model.ExpressionSuggestion;
import com.squid.kraken.v4.model.ExpressionSuggestionItem;
import com.squid.kraken.v4.model.ResultInfo;
import com.squid.kraken.v4.model.NavigationItem;
import com.squid.kraken.v4.model.NavigationQuery;
import com.squid.kraken.v4.model.NavigationResult;
import com.squid.kraken.v4.model.ObjectType;
import com.squid.kraken.v4.model.Problem;
import com.squid.kraken.v4.model.ValueType;
import com.squid.kraken.v4.model.ViewQuery;
import com.squid.kraken.v4.model.ViewReply;
import com.squid.kraken.v4.model.DataTable.Col;
import com.squid.kraken.v4.model.DataTable.Row;
import com.squid.kraken.v4.model.Dimension.Type;
import com.squid.kraken.v4.model.NavigationQuery.Style;
import com.squid.kraken.v4.model.Problem.Severity;
import com.squid.kraken.v4.persistence.AppContext;
import com.squid.kraken.v4.vegalite.VegaliteSpecs;
import com.squid.kraken.v4.vegalite.VegaliteSpecs.Encoding;

/**
 * This call is responsible for generating the HTML code for the API
 * @author sergefantino
 *
 */
public class AnalyticsServiceHTMLGenerator implements AnalyticsServiceConstants {
	
	private AnalyticsServiceBaseImpl service;

	/**
	 * 
	 */
	public AnalyticsServiceHTMLGenerator(AnalyticsServiceBaseImpl service) {
		this.service = service;
	}
	
	private String getToken() {
		return service.getUserContext().getToken().getOid();
	}

	private void createHTMLtitle(StringBuilder html, String title, String BBID, URI backLink, String docAnchor) {
		html.append("<div class=\"logo\"><span>Analytics Rest <b style='color:white;'>API</b> Viewer / STYLE=HTML</span>");
		html.append("<hr style='margin:0px;'></div>");
		html.append("<h3>");
		if (title!=null) {
			html.append("<i class=\"fa fa-folder-open-o\" aria-hidden=\"true\"></i>\n" + 
					title);
		} 
		if (BBID!=null) {
			html.append("&nbsp;[ID="+BBID+"]");
		}
		if (backLink!=null) html.append("&nbsp;<a href=\""+backLink+"\">back to parent</a>");
		html.append("</h3>");
		html.append("<a target='OB API DOC' href='https://openbouquet.github.io/slate/"+(docAnchor!=null?docAnchor:"")+"' ><span class=\"label label-info\">API doc</span></a>");
		html.append("<hr>");
	}
	
	private StringBuilder createHTMLHeader(String title) {
		StringBuilder html = new StringBuilder("<!DOCTYPE html><html xmlns=\"http://www.w3.org/1999/xhtml\" xml:lang=\"en\"><head><meta charset='utf-8'><title>"+title+"</title>");
		// bootstrap & jquery
		html.append("<!-- Latest compiled and minified CSS -->\n" + 
				"<link rel=\"stylesheet\" href=\"https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/css/bootstrap.min.css\">\n" + 
				"\n" + 
				"<!-- jQuery library -->\n" + 
				"<script src=\"https://ajax.googleapis.com/ajax/libs/jquery/3.1.1/jquery.min.js\"></script>\n" + 
				"\n" + 
				"<!-- Latest compiled JavaScript -->\n" + 
				"<script src=\"https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/js/bootstrap.min.js\"></script>\n");
		// font
		html.append("<link rel=\"stylesheet\" href=\"https://use.fontawesome.com/1ea8d4d975.css\">");
		// style
		html.append("<style>"
				+ "* {font-family: \"Helvetica Neue\",Helvetica,Arial,sans-serif; color: #666; }"
				+ "table.data {border-collapse: collapse;width: 100%;}"
				+ "th, td {text-align: left;padding: 8px; vertical-align: top;}"
				+ ".data tr:nth-child(even) {background-color: #f2f2f2}"
				+ ".data th {background-color: #ee7914;color: white;}"
				+ ".vega-actions a {margin-right:10px;}"
				+ "hr {border: none; "
				+ "color: Gainsboro ;\n" + 
				"background-color: Gainsboro ;\n" + 
				"height: 3px;}"
				+ "input[type=date], input[type=text], select {\n" + 
				"    padding: 4px 4px;\n" + 
				"    margin: 4px 0;\n" + 
				"    display: inline-block;\n" + 
				"    border: 1px solid #ccc;\n" + 
				"    border-radius: 4px;\n" + 
				"    box-sizing: border-box;\n" + 
				"}\n" + 
				"button[type=submit] {\n" + 
			    "	 font-size: 1.3em;" +
				"    width: 200px;\n" + 
				"    background-color: #ee7914;\n" + 
				"    color: white;\n" + 
				"    padding: 14px 20px;\n" + 
				"    margin: 0 auto;\n" + 
				"    display: block;" + 
				"    border: none;\n" + 
				"    border-radius: 4px;\n" + 
				"    cursor: pointer;\n" + 
				"}\n" +
				"button[type=submit]:hover {\n" + 
				"    background-color: #ab570e;\n" + 
				"}\n" +
				"input[type=submit] {\n" + 
			    "	 font-size: 1.3em;" +
				"    width: 200px;\n" + 
				"    background-color: #ee7914;\n" + 
				"    color: white;\n" + 
				"    padding: 14px 20px;\n" + 
				"    margin: 0 auto;\n" + 
				"    display: block;" + 
				"    border: none;\n" + 
				"    border-radius: 4px;\n" + 
				"    cursor: pointer;\n" + 
				"}\n" +
				"input[type=text].q {\n" + 
				"    box-sizing: border-box;\n" + 
				"    border: 2px solid #ccc;\n" + 
				"    border-radius: 4px;\n" + 
				"    font-size: 16px;\n" + 
				"    background-color: white;\n" + 
				"    background-position: 10px 10px; \n" + 
				"    background-repeat: no-repeat;\n" + 
				"    padding: 12px 20px 12px 40px;" +
				"}"+
				"input[type=submit]:hover {\n" + 
				"    background-color: #ab570e;\n" + 
				"}\n" +
				"fieldset {\n" + 
				"    margin-top: 40px;\n" + 
				"}\n" + 
				"legend {\n" + 
				"    font-size: 1.3em;\n" + 
				"}\n" + 
				"table.controls {\n" + 
				"    border-collapse:separate; \n" + 
				"    border-spacing: 0 20px;\n" + 
				"}\n" + 
				"body {\n" + 
				"    margin-top: 0px;\n" + 
				"    margin-bottom: 0px;\n" + 
				"    margin-left: 5px;\n" + 
				"    margin-right: 5px;\n" + 
				"}\n" + 
				".footer a, .footer a:hover, .footer a:visited {\n" + 
				"    color: White;\n" + 
				"}\n" +
				".footer {\n" + 
				"    background: #5A5A5A;\n" + 
				"    padding: 10px;\n" + 
				"    margin: 20px -8px -8px -8px;\n" + 
				"}\n" + 
				".footer p {\n" + 
				"    text-align: center;\n" + 
				"    color: White;\n" + 
				"    width: 100%;\n" + 
				"}\n" + 
				".header {\n" + 
				"    margin: -8px 0 0 0;\n" + 
				"}\n" + 
				"h2 { margin-bottom:0px;}\n" +
				"h4 { margin-bottom:0px;}\n" +
				".logo {\n" + 
				"    margin-left: -5px;\n" + 
				"    margin-right: -5px;\n" + 
				"    height: 32px;\n" + 
				"    background: url('data:image/png;base64,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');\n" +
				"    background-repeat: no-repeat;\n" + 
				"	 background-color: #ee7914;\n" +
				"}\n" + 
				".logo span {\n" + 
				"    line-height: 32px;\n" + 
				"    vertical-align: middle;\n" + 
				"    color: #5e5e5e;\n" + 
				"    padding-left: 132px;\n" + 
				"    font-size: 20px;\n" + 
				"}\n" + 
				".header hr {\n" + 
				"    margin: 0 -8px 0 -8px;\n" + 
				"}\n" +
				".period input {\n" + 
				"    margin-left: 10px;\n" + 
				"    margin-right: 10px;\n" + 
				"}\n" + 
				".instructions {\n" + 
				"    font-size: 1.2em;\n" + 
				"    font-style: italic;\n" + 
				"}\n" +
				".tooltip-inner {\n" + 
				"    min-width: 250px;n" + 
				"    max-width: 500px;n" + 
				"    background-color: #aaaaaa;" +
				"}" +
				"</style>");
		// drag & drop support
		html.append("<script>\n" + 
				"function allowDrop(ev) {\n" + 
				"    ev.preventDefault();\n" + 
				"}\n" + 
				"function drag(ev, text) {\n" + 
				"    ev.dataTransfer.setData(\"text\", \"'\"+text+\"'\");\n" + 
				"}\n" + 
				"function drop(ev) {\n" + 
				"    ev.preventDefault();\n" + 
				"    var data = ev.dataTransfer.getData(\"text\");\n" + 
				"    ev.target.value = data;\n" +
				"}\n" + 
				"</script>");
		html.append("</head><body>");
		return html;
	}
	

	private void createHTMLinputArray(StringBuilder html, String type, String name, List<? extends Object> values) {
		html.append("<div id='fields_"+name+"'>");
		if (values==null || values.isEmpty()) {
			html.append("<input type=\""+type+"\" style='width:100%;'  name=\""+name+"\" value=\"\" placeholder=\"type formula\"><br>");
		} else {
			boolean first = true;
			for (Object value : values) {
				if (!first) html.append("<br>"); else first=false;
				html.append("<input type=\""+type+"\" style='width:100%;'  name=\""+name+"\" value=\""+getFieldValue(value.toString())+"\">");
			}
			if (!first) html.append("<br>");
			//html.append("<input type=\""+type+"\" size=100 name=\""+name+"\" value=\"\" placeholder=\"type formula\">");
		}
		html.append("</div>");
		// add the add button
		html.append("<script type='text/javascript'>//<![CDATA[\n" + 
				"\n" + 
				"        function addField_"+name+"(){\n" + 
				"            var container = document.getElementById(\"fields_"+name+"\");\n" + 
				"            var input = document.createElement(\"input\");\n" + 
				"            input.type = '"+type+"';\n" + 
				"            input.style = 'width:100%;';\n" + 
				"            input.name = '"+name+"';\n" + 
				"            input.placeholder = 'type formula';\n" + 
				"            container.appendChild(input);\n" + 
				"            container.appendChild(document.createElement(\"br\"));\n" + 
				"        }\n" + 
				"//]]> \n" + 
				"\n" + 
				"</script>\n");
		html.append("<button type='button' onclick='addField_"+name+"()'>Add +</button>");
	}
	
	private void createHTMLpagination(StringBuilder html, AnalyticsQuery query, DataTable data) {
		long lastRow = (data.getStartIndex()+data.getRows().size());
		long firstRow = data.getRows().size()>0?(data.getStartIndex()+1):0;
		html.append("<div style='padding-top:5px;'>rows from "+firstRow+" to "+lastRow+" out of "+data.getTotalSize()+" records");
		if (data.getFullset()) {
			html.append(" (the query is complete)");
		} else {
			html.append(" (the query has more data)");
		}
		if (lastRow<data.getTotalSize()) {
			// go to next page
			HashMap<String, Object> override = new HashMap<>();
			override.put(START_INDEX_PARAM, lastRow);
			URI nextLink = service.buildAnalyticsQueryURI(service.getUserContext(), query, null, null, Style.HTML, override);
			html.append("&nbsp;[<a href=\""+StringEscapeUtils.escapeHtml4(nextLink.toString())+"\">next</a>]");
		}
		html.append("</div><div>");
		if (data.isFromSmartCache()) {
			html.append("data from smart-cache, last computed "+data.getExecutionDate());
		} else if (data.isFromCache()) {
			html.append("data from cache, last computed "+data.getExecutionDate());
		} else {
			html.append("fresh data just computed at "+data.getExecutionDate());
		}
		// add links
		html.append("<div>");
		createHTMLdataLinks(html, query);
		html.append("</div>");
		html.append("</div><br>");
	}
	
	private void createHTMLdataLinks(StringBuilder html, AnalyticsQuery query) {
		// add links
		{ // for SQL
			URI sqlLink = service.buildAnalyticsQueryURI(service.getUserContext(), query, "SQL", null, Style.HTML, null);
			html.append("&nbsp;[<a href=\""+StringEscapeUtils.escapeHtml4(sqlLink.toString())+"\">SQL</a>]");
		}
		{ // for JSON export
			URI jsonExport = service.buildAnalyticsQueryURI(service.getUserContext(), query, "TABLE", null, Style.HUMAN, null);
			html.append("&nbsp;[<a href=\""+StringEscapeUtils.escapeHtml4(jsonExport.toString())+"\">JSON</a>]");
		}
		{ // for CSV export
			URI csvExport = service.buildAnalyticsExportURI(service.getUserContext(), query, ".csv");
			html.append("&nbsp;[<a href=\""+StringEscapeUtils.escapeHtml4(csvExport.toString())+"\">Export CSV</a>]");
		}
		{ // for XLS export
			URI xlsExport = service.buildAnalyticsExportURI(service.getUserContext(), query, ".xls");
			html.append("&nbsp;[<a href=\""+StringEscapeUtils.escapeHtml4(xlsExport.toString())+"\">Export XLS</a>]");
		}
	}
	
	private void createHTMLpagination(StringBuilder html, ViewQuery query, ResultInfo info) {
		long lastRow = (info.getStartIndex()+info.getPageSize());
		long firstRow = info.getTotalSize()>0?(info.getStartIndex()+1):0;
		html.append("<div style='padding-top:5px;'>rows from "+firstRow+" to "+lastRow+" out of "+info.getTotalSize()+" records");
		if (info.isComplete()) {
			html.append(" (the query is complete)");
		} else {
			html.append(" (the query has more data)");
		}
		if (lastRow<info.getTotalSize()) {
			// go to next page
			HashMap<String, Object> override = new HashMap<>();
			override.put(START_INDEX_PARAM, lastRow);
			URI nextLink = service.buildAnalyticsViewURI(service.getUserContext(), query, null, null, Style.HTML, override);
			html.append("&nbsp;[<a href=\""+StringEscapeUtils.escapeHtml4(nextLink.toString())+"\">next</a>]");
		}
		html.append("</div>");
		if (info.isFromSmartCache()) {
			html.append("<div>data from smart-cache, last computed "+info.getExecutionDate()+"</div>");
		} else if (info.isFromCache()) {
			html.append("<div>data from cache, last computed "+info.getExecutionDate()+"</div>");
		} else {
			html.append("<div>fresh data just computed at "+info.getExecutionDate()+"</div>");
		}
	}

	/**
	 * Simple page with SQL prettified
	 * @param string
	 * @return
	 */
	public Response createHTMLsql(String sql) {
		StringBuilder html = new StringBuilder("<html><head>");
		html.append(
				"<script src='https://cdn.rawgit.com/google/code-prettify/master/loader/run_prettify.js?lang=sql'></script>");
		html.append("</head><body>");
		html.append(
				"<pre class='prettyprint lang-sql' style='white-space: pre-wrap;white-space: -moz-pre-wrap;white-space: -pre-wrap;white-space: -o-pre-wrap;word-wrap: break-word;padding:0px;margin:0px'>");
		html.append(StringEscapeUtils.escapeHtml4(sql));
		html.append("</pre>");
		html.append("</body></html>");
		return Response.ok(html.toString(),"text/html").build();
	}
	
	/**
	 * the navigation view
	 * @param ctx
	 * @param query
	 * @param result
	 * @return
	 */
	public Response createHTMLPageList(AppContext ctx, NavigationQuery query, NavigationResult result) {
		String title = (query.getParent()!=null && query.getParent().length()>0)?query.getParent():"Root";
		StringBuilder html = createHTMLHeader("List: "+title);
		createHTMLtitle(html, title, null, result.getParent().getUpLink(),"#list-available-content");
		// form
		html.append("<form><table>");
		html.append("<tr><td><input size=50 class='q' type='text' name='q' placeholder='filter the list' value='"+(query.getQ()!=null?query.getQ():"")+"'></td>"
				+ "<td><input type=\"submit\" value=\"Filter\"></td></tr>");
		html.append("<input type='hidden' name='parent' value='"+(query.getParent()!=null?query.getParent():"")+"'>");
		if (query.getStyle()!=null)
			html.append("<input type='hidden' name='style' value='"+(query.getStyle()!=null?query.getStyle():"")+"'>");
		if (query.getVisibility()!=null)
			html.append("<input type='hidden' name='visibility' value='"+(query.getVisibility()!=null?query.getVisibility():"")+"'>");
		if (query.getHiearchy()!=null)
			html.append("<input type='hidden' name='hierarchy' value='"+query.getHiearchy()+"'>");
		html.append("<input type='hidden' name='access_token' value='"+ctx.getToken().getOid()+"'>");
		html.append("</table></form>");
		//
		// parent description
		if (result.getParent()!=null && result.getParent().getDescription()!=null && result.getParent().getDescription().length()>0) {
			html.append("<p><i>"+result.getParent().getDescription()+"</i></p>");
		}
		// coontent
		if (result.getChildren().isEmpty()) {
			html.append("<p><center>empty folder, nothing to show</center></p>");
		}
		html.append("<table style='border-collapse:collapse'>");
		for (NavigationItem item : result.getChildren()) {
			html.append("<tr>");
			html.append("<td valign='top'>"+item.getType()+"</td>");
			if (item.getLink()!=null) {
				html.append("<td valign='top'><a href=\""+StringEscapeUtils.escapeHtml4(item.getLink().toString())+"\">"+item.getName()+"</a>");
			} else {
				html.append("<td valign='top'>"+item.getName());
			}
			if (item.getObjectLink()!=null) {
				html.append("&nbsp;[<a href=\""+StringEscapeUtils.escapeHtml4(item.getObjectLink().toString())+"\">info</a>]");
			}
			if (item.getViewLink()!=null) {
				html.append("&nbsp;[<a href=\""+StringEscapeUtils.escapeHtml4(item.getViewLink().toString())+"\">view</a>]");
			}
			if (item.getDescription()!=null && item.getDescription().length()>0) {
				html.append("<br><i>"+(item.getDescription()!=null?item.getDescription():"")+"</i>");
			}
			html.append("</td>");
			if (item.getAttributes()!=null) {
				for (Entry<String, String> entry : item.getAttributes().entrySet()) {
					html.append("<td valign='top'>"+entry.getKey()+"="+entry.getValue()+"</td>");
				}
			}
		}
		html.append("</table>");
		createHTMLAPIpanel(html, "listContent");
		html.append("</body></html>");
		return Response.ok(html.toString(),"text/html").build();
	}

	/**
	 * The query view
	 * @param userContext 
	 * @param dataTable 
	 * @return
	 */
	public Response createHTMLPageTable(AppContext userContext, Space space, AnalyticsQuery query, DataTable data) {
		String title = space!=null?getPageTitle(space):null;
		StringBuilder html = createHTMLHeader("Query: "+title);
		createHTMLtitle(html, title, query.getBBID(), getParentLink(space),"#query-a-bookmark-or-domain");
		createHTMLproblems(html, query.getProblems());
		html.append("<form>");
		if (data!=null) {
			html.append("<h4 style='font-family:Helvetica Neue,Helvetica,Arial,sans-serif;'>Query Result</h4><hr>");
			html.append("<div style='max-height:200px;overflow:scroll;'>");
			html.append("<table class='data'><tr>");
			html.append("<th></th>");
			for (Col col : data.getCols()) {
				html.append("<th>"+col.getName()+"</th>");
			}
			html.append("</tr>");
			int i=1;
			if (query.getStartIndex()!=null) {
				i=query.getStartIndex()+1;
			}
			for (Row row : data.getRows()) {
				html.append("<tr>");
				html.append("<td valign='top'>#"+(i++)+"</td>");
				for (Col col : data.getCols()) {
					Object value = row.getV()[col.getPos()];
					if (value!=null) {
						if (col.getFormat()!=null && col.getFormat().length()>0) {
							try {
								value = String.format(col.getFormat(), value);
							} catch (IllegalFormatException e) {
								// ignore
							}
						}
					} else {
						value="";
					}
					html.append("<td valign='top'>"+value+"</td>");
				}
				html.append("</tr>");
			}
			html.append("</table>");
			html.append("</div>");
			html.append("<div style='float:left;padding:5px'><button type='submit' value='Query'><i class=\"fa fa-refresh\" aria-hidden=\"true\"></i>&nbsp;Query</button></div>");
			{ // for View
				HashMap<String, Object> override = new HashMap<>();
				override.put(LIMIT_PARAM, null);
				override.put(MAX_RESULTS_PARAM, null);
				URI link = service.buildAnalyticsViewURI(service.getUserContext(), new ViewQuery(query), null, "ALL", Style.HTML, override);//(userContext, query, "SQL", null, Style.HTML, null);
				html.append("<div style='float:left;padding:5px'><button type='submit' value='Visualize' formaction=\""+StringEscapeUtils.escapeHtml4(link.toString())+"\"><i class=\"fa fa-bar-chart\" aria-hidden=\"true\"></i>&nbsp;Visualize</button></div>");
			}
			createHTMLpagination(html, query, data);
		} else {
			html.append("<i>Result is not available, it's probably due to an error</i>");
			html.append("<p>");
			createHTMLdataLinks(html, query);
			html.append("</p<br>");
			html.append("<div style='float:left;padding:5px;'><input type='submit' value='Refresh'></div>");
			html.append("<div style='clear:both;'></div>");
		}
		// the parameters pannel
		html.append("<div>");
		html.append("<div style='width:50%;float:left;'>");
		html.append("<div style='padding-right:15px;'>");
		html.append("<h4 style='font-family:Helvetica Neue,Helvetica,Arial,sans-serif;'>Query Parameters</h4><hr>");
		createHTMLswaggerLink(html, "runAnalysis");
		createHTMLfilters(html, query);
		html.append("<table style='width:100%;'>");
		html.append("<tr><td valign='top' style='width:100px;'><a href=\"#\" data-toggle=\"tooltip\" data-placement=\"right\" title=\""+GROUPBY_DOC+"\">groupBy:</a>");
		html.append("</td><td>");
		createHTMLinputArray(html, "text", "groupBy", query.getGroupBy());
		html.append("</td></tr>");
		html.append("<tr><td valign='top'><a href=\"#\" data-toggle=\"tooltip\" data-placement=\"right\" title=\""+METRICS_DOC+"\">metrics:</a>");
		html.append("</td><td>");
		createHTMLinputArray(html, "text", "metrics", query.getMetrics());
		html.append("</td></tr>");
		html.append("<tr><td valign='top'><a href=\"#\" data-toggle=\"tooltip\" data-placement=\"right\" title=\""+ROLLUP_DOC+"\">rollup:</a>");
		html.append("</td><td>");
		createHTMLinputArray(html, "text", "rollup", query.getRollups());
		html.append("</td></tr>");
		html.append("<tr><td valign='top'><a href=\"#\" data-toggle=\"tooltip\" data-placement=\"right\" title=\""+ORDERBY_DOC+"\">orderBy:</a>");
		html.append("</td><td>");
		createHTMLinputArray(html, "text", "orderBy", query.getOrderBy());
		html.append("</td></tr>");
		html.append("<tr><td valign='top'><a href=\"#\" data-toggle=\"tooltip\" data-placement=\"right\" title=\""+LIMIT_DOC+"\">limit:</a>");
		html.append("</td><td>");
		html.append("<input type=\"text\" name=\"limit\" value=\""+getFieldValue(query.getLimit(),0)+"\">");
		html.append("</td></tr>");
		html.append("<tr><td valign='top'><a href=\"#\" data-toggle=\"tooltip\" data-placement=\"right\" title=\""+OFFSET_DOC+"\">offset:</a>");
		html.append("</td><td>");
		html.append("<input type=\"text\" name=\"offset\" value=\""+getFieldValue(query.getOffset(),0)+"\">");
		html.append("</td></tr>");
		html.append("<tr><td valign='top'><a href=\"#\" data-toggle=\"tooltip\" data-placement=\"right\" title=\""+MAX_RESULTS_DOC+"\">maxResults:</a>");
		html.append("</td><td>");
		html.append("<input type=\"text\" name=\"maxResults\" value=\""+getFieldValue(query.getMaxResults(),100)+"\">");
		html.append("</td></tr>");
		html.append("<tr><td valign='top'><a href=\"#\" data-toggle=\"tooltip\" data-placement=\"right\" title=\""+START_INDEX_DOC+"\">startIndex:</a>");
		html.append("</td><td>");
		html.append("<input type=\"text\" name=\"startIndex\" value=\""+getFieldValue(query.getStartIndex(),0)+"\">");
		html.append("</td></tr>");
		html.append("</table>"
				+ "<input type=\"hidden\" name=\"style\" value=\"HTML\">"
				+ "<input type=\"hidden\" name=\"access_token\" value=\""+userContext.getToken().getOid()+"\">");
		html.append("</form>");
		// the scope pannel
		html.append("</div></div><div style='width:50%;float:left;'>");
		if (space!=null) createHTMLscope(userContext, html, space, query);
		html.append("</div>");
		html.append("</div>");
		createHTMLAPIpanel(html, "runAnalysis");
		html.append("</body></html>");
		return Response.ok(html.toString(),"text/html").build();
	}
	
	/**
	 * the /view view (vegalite)
	 * @param userContext 
	 * @param space
	 * @param view
	 * @param info
	 * @param reply
	 * @return
	 */
	public Response createHTMLPageView(AppContext userContext, Space space, ViewQuery view, ResultInfo info, ViewReply reply) {
		String title = getPageTitle(space);
		StringBuilder html = createHTMLHeader("View: "+title);
		html.append("<script src=\"https://d3js.org/d3.v3.min.js\" charset=\"utf-8\"></script>\r\n<script src=\"https://vega.github.io/vega/vega.js\" charset=\"utf-8\"></script>\r\n<script src=\"https://vega.github.io/vega-lite/vega-lite.js\" charset=\"utf-8\"></script>\r\n<script src=\"https://vega.github.io/vega-editor/vendor/vega-embed.js\" charset=\"utf-8\"></script>\r\n\r\n");
		html.append("<body>");
		createHTMLtitle(html, title, view.getBBID(), getParentLink(space),"#view-a-bookmark-or-domain");
		createHTMLproblems(html, reply.getQuery().getProblems());
		// vega lite preview
		html.append("<div>");
		html.append("<h4 style='font-family:Helvetica Neue,Helvetica,Arial,sans-serif;'>View Result</h4><hr>");
		html.append("<center>");
		html.append("<div id=\"vis\"></div>\r\n\r\n<script>\r\nvar embedSpec = {\r\n  mode: \"vega-lite\", renderer:\"svg\",  spec:");
		html.append(writeVegalightSpecs(reply.getResult()));
		Encoding channels = reply.getResult().encoding;
		html.append("}\r\nvg.embed(\"#vis\", embedSpec, function(error, result) {\r\n  // Callback receiving the View instance and parsed Vega spec\r\n  // result.view is the View, which resides under the '#vis' element\r\n});\r\n</script>\r\n");
		html.append("</center><hr>");
		// refresh
		html.append("<form>");
		html.append("<div style='float:left;padding:5px;'><button type='submit' value='Visualize'><i class=\"fa fa-bar-chart\" aria-hidden=\"true\"></i>&nbsp;Visualize</button></div>");
		// data-link
		URI querylink = service.buildAnalyticsQueryURI(service.getUserContext(), reply.getQuery(), "RECORDS", "ALL", Style.HTML, null);
		html.append("<div style='float:left;padding:5px;'><button type='submit' value='Query' formaction=\""+StringEscapeUtils.escapeHtml4(querylink.toASCIIString())+"\"><i class=\"fa fa-refresh\" aria-hidden=\"true\"></i>&nbsp;Query</button></div>");
		createHTMLpagination(html, view, info);
		html.append("<div style='clear:both;'></div>");
		html.append("</div>");
		html.append("</div>");
		// parameters
		html.append("<div>");
		html.append("<div style='width:50%;float:left;'>");
		html.append("<div style='padding-right:15px;'>");
		html.append("<h4 style='font-family:Helvetica Neue,Helvetica,Arial,sans-serif;'>View Parameters</h4><hr>");
		// view parameter
		html.append("<table style='width:100%;'>"
				+ "<tr><td style='width:100px;'>x</td><td><input type=\"text\" style='width:100%;' name=\"x\" value=\""+getFieldValue(view.getX())+"\"><br>"+(channels.x!=null?"as <b>"+channels.x.field+"</b>":"")+"</td></tr>"
				+ "<tr><td>y</td><td><input type=\"text\" style='width:100%;' name=\"y\" value=\""+getFieldValue(view.getY())+"\"><br>"+(channels.y!=null?"as <b>"+channels.y.field+"</b>":"")+"</td></tr>"
				+ "<tr><td>color</td><td><input type=\"text\" style='width:100%;' name=\"color\" value=\""+getFieldValue(view.getColor())+"\"><br>"+(channels.color!=null?"as <b>"+channels.color.field+"</b>":"")+"</td></tr>"
				+ "<tr><td>size</td><td><input type=\"text\" style='width:100%;' name=\"size\" value=\""+getFieldValue(view.getSize())+"\"><br>"+(channels.size!=null?"as <b>"+channels.size.field+"</b>":"")+"</td></tr>"
				+ "<tr><td>column</td><td><input type=\"text\" style='width:100%;' name=\"column\" value=\""+getFieldValue(view.getColumn())+"\"><br>"+(channels.column!=null?"as <b>"+channels.column.field+"</b>":"")+"</td></tr>"
				+ "<tr><td>row</td><td><input type=\"text\" style='width:100%;' name=\"row\" value=\""+getFieldValue(view.getRow())+"\"><br>"+(channels.row!=null?"as <b>"+channels.row.field+"</b>":"")+"</td></tr>");
		html.append("</table>");
		// the other (query) parameters
		html.append("<h4 style='font-family:Helvetica Neue,Helvetica,Arial,sans-serif;'>Query Parameters</h4><hr>");
		html.append("<p>Those parameters are inherited from the /query API</p>");
		// filters
		createHTMLfilters(html, reply.getQuery());
		// metrics -- display the actual metrics
		html.append("<table style='width:100%;'>");
		html.append("<tr><td valign='top' style='width:100px;'><a href=\"#\" data-toggle=\"tooltip\" data-placement=\"right\" title=\""+GROUPBY_DOC+"\">groupBy:</a");
		html.append("</td><td>");
		createHTMLinputArray(html, "text", "groupBy", reply.getQuery().getGroupBy());
		html.append("</td></tr>");
		html.append("<tr><td valign='top' style='width:100px;'><a href=\"#\" data-html='true' data-toggle=\"tooltip\" data-placement=\"right\" title=\"Use the metrics parameters if you want to view multiple metrics on the same graph. Then you can use the <b>__VALUE</b> expression in channel to reference the metrics' value, and the <b>__METRICS</b> to get the metrics' name as a series.<br>If you need only a single metrics, you can directly define it in a channel, e.g. <code>y=count()</code>.<br>"+METRICS_DOC+"\">metrics:</a>");
		html.append("</td><td>");
		createHTMLinputArray(html, "text", "metrics", reply.getQuery().getMetrics());
		html.append("</td></tr>");
		// limits, maxResults, startIndex
		html.append("<tr><td valign='top'><a href=\"#\" data-toggle=\"tooltip\" data-placement=\"right\" title=\""+LIMIT_DOC+"\">limit:</a>");
		html.append("</td><td>");
		html.append("<input type=\"text\" name=\"limit\" value=\""+getFieldValue(reply.getQuery().getLimit(),0)+"\">");
		html.append("</td></tr>");
		html.append("<tr><td valign='top'><a href=\"#\" data-toggle=\"tooltip\" data-placement=\"right\" title=\""+OFFSET_DOC+"\">offset:</a>");
		html.append("</td><td>");
		html.append("<input type=\"text\" name=\"offset\" value=\""+getFieldValue(reply.getQuery().getOffset(),0)+"\">");
		html.append("</td></tr>");
		html.append("<tr><td valign='top'><a href=\"#\" data-toggle=\"tooltip\" data-placement=\"right\" title=\""+MAX_RESULTS_DOC+"\">maxResults:</a>");
		html.append("</td><td>");
		html.append("<input type=\"text\" name=\"maxResults\" value=\""+getFieldValue(reply.getQuery().getMaxResults(),100)+"\">");
		html.append("</td></tr>");
		html.append("<tr><td valign='top'><a href=\"#\" data-toggle=\"tooltip\" data-placement=\"right\" title=\""+START_INDEX_DOC+"\">startIndex:</a>");
		html.append("</td><td>");
		html.append("<input type=\"text\" name=\"startIndex\" value=\""+getFieldValue(reply.getQuery().getStartIndex(),0)+"\">");
		html.append("</td></tr>");
		html.append("</table>"
				+ "<input type=\"hidden\" name=\"style\" value=\"HTML\">"
				+ "<input type=\"hidden\" name=\"access_token\" value=\""+space.getUniverse().getContext().getToken().getOid()+"\">"
				//+ "<input type=\"submit\" value=\"Refresh\">"
				+ "</form>");
		html.append("</div>");
		html.append("</div>");
		// scope
		html.append("<div style='width:50%;float:left;'>");
		createHTMLscope(userContext, html, space, reply.getQuery());
		html.append("</div>");
		html.append("</div>");
		html.append("<div style='clear:both;'></div>");
		createHTMLAPIpanel(html, "viewAnalysis");
		html.append("</body>\r\n</html>");
		return Response.ok(html.toString(), "text/html; charset=UTF-8").build();
	}

	/**
	 * create a filter HTML snippet
	 * @param query
	 * @return
	 */
	private void createHTMLfilters(StringBuilder html, AnalyticsQuery query) {
		html.append("<table style='width:100%;'><tr><td style='width:100px;'>");
		// period
		html.append("<a href=\"#\" data-toggle=\"tooltip\" data-placement=\"right\" title=\""+PERIOD_DOC+"\">period:</a>");
		html.append("</td><td colspan=2>");
		html.append("<input type='text' style='width:100%;' name='period' value='"+getFieldValue(query.getPeriod())+"'>");
		html.append("</td></tr>");
		// timeframe
		html.append("<tr><td>");
		html.append("<a href=\"#\" data-toggle=\"tooltip\" data-placement=\"right\" title=\""+TIMEFRAME_DOC+"\">timeframe:</a>");
		html.append("</td><td>");
		html.append("from:&nbsp;<input type='text' style='width:100%;' name='timeframe' value='"+getDate(query.getTimeframe(),0)+"'>");
		html.append("</td><td>");
		html.append("to:&nbsp;<input type='text' style='width:100%;' name='timeframe' value='"+getDate(query.getTimeframe(),1)+"'>");
		html.append("</td></tr>");
		// compare
		html.append("<tr><td>");
		html.append("<a href=\"#\" data-toggle=\"tooltip\" data-placement=\"right\" title=\""+COMPARETO_DOC+"\">compareTo:</a>");
		html.append("</td><td>");
		html.append("from:&nbsp;<input type='text' style='width:100%;' name='compareTo' value='"+getDate(query.getCompareTo(),0)+"'>");
		html.append("</td><td>");
		html.append("to:&nbsp;<input type='text' style='width:100%;' name='compareTo' value='"+getDate(query.getCompareTo(),1)+"'>");
		html.append("</td></tr>");
		// filters
		html.append("<tr><td>");
		html.append("<a href=\"#\" data-toggle=\"tooltip\" data-placement=\"right\" title=\""+FILTERS_DOC+"\">filters:</a>");
		html.append("</td><td colspan=2>");
		/*
		if (query.getFilters()!=null && query.getFilters().size()>0) {
			for (String filter : query.getFilters()) {
				html.append("<input type='text' size=50 name='filters' value='"+getFieldValue(filter)+"'>&nbsp;");
			}
		}
		html.append("<input type='text' size=50 name='filters' value='' placeholder='type formula'>");
		*/
		createHTMLinputArray(html, "text", "filters", query.getFilters());
		html.append("</td></tr></table>");
	}
	
	private static final String axis_style = "display: inline-block;border:1px solid;border-radius:5px;background-color:LavenderBlush ;margin:1px;";
	private static final String metric_style = "display: inline-block;border:1px solid;border-radius:5px;background-color:Lavender;margin:1px;";
	private static final String func_style = "display: inline-block;border:1px solid;border-radius:5px;background-color:ghostwhite;margin:1px;";
	private static final String other_style = "display: inline-block;border:1px solid;border-radius:5px;background-color:azure;margin:1px;";
	
	private void createHTMLscope(AppContext userContext, StringBuilder html, Space space, AnalyticsQuery query) {
		html.append("<h4 style='font-family:Helvetica Neue,Helvetica,Arial,sans-serif;'>Query Scope</h4><hr>");
		{
			UriBuilder builder = service.getPublicBaseUriBuilder().
					path("/analytics/{"+BBID_PARAM_NAME+"}/query");
			builder.queryParam(STYLE_PARAM, Style.HTML);
			builder.queryParam("access_token", userContext.getToken().getOid());
			URI uri = builder.build(space.getBBID(Style.ROBOT));
			html.append("<p>Query defined on domain: <a href=\""+StringEscapeUtils.escapeHtml4(uri.toString())+"\">"+StringEscapeUtils.escapeHtml4(space.getBBID(Style.HUMAN))+"</a>");
			URI scopeLink = service.getPublicBaseUriBuilder().path("/analytics/{reference}/scope").queryParam("style", Style.HTML).queryParam("access_token", getToken()).build(query.getBBID());
			html.append("&nbsp;[<a href=\""+StringEscapeUtils.escapeHtml4(scopeLink.toASCIIString())+"\">/scope</a>]");
			html.append("</p>");
		}
		//html.append("<i>This is the list of objects you can combine to build expressions in the query scope. Drag & Drop expression into input parameters on the left.</i>");
		html.append("<p id=\"query-instructions\"><i class=\"fa fa-info-circle fa-lg\" aria-hidden=\"true\"></i>&nbsp;You can either build expressions manually, or drag and drop objects from Query Scope to Query parameters.</p>");
		html.append("<div class=\"containerx\">");
		html.append("<ul class='nav nav-tabs'>");
		html.append("<li class=\"active\"><a data-toggle=\"tab\" href=\"#dimensions\">Dimensions</a></li>");
		html.append("<li><a data-toggle=\"tab\" href=\"#metrics\">Metrics</a></li>");
		html.append("<li><a data-toggle=\"tab\" href=\"#functions\">Functions</a></li>");
		html.append("</ul>");
		html.append("<div class=\"tab-content\">");
		html.append("<div id=\"dimensions\" class=\"tab-pane fade in active\">");
		//html.append("<div style='max-height:300px;overflow:scroll;'>");
		for (Axis axis : space.A(true)) {// only print the visible scope
			try {
				IDomain image = axis.getDefinitionSafe().getImageDomain();
				if (!image.isInstanceOf(IDomain.OBJECT)) {
					DimensionIndex index = axis.getIndex();
					html.append("<span draggable='true' style='"+axis_style+"'");
					html.append(" ondragstart='drag(event,\""+index.getDimensionName()+"\")'>");
					if (index.getErrorMessage()==null) {
						html.append("&nbsp;"+index.getDimensionName()+"&nbsp;");
					} else {
						html.append("&nbsp;<del>"+index.getDimensionName()+"</del>&nbsp;");
					}
					html.append("</span>");
					// add description, type, error
					ExpressionAST expr = axis.getDefinitionSafe();
					html.append(":"+getExpressionValueType(expr).toString());
					if (index.getErrorMessage()!=null) {
						html.append("&nbsp;<b>Error:"+index.getErrorMessage()+"</b>");
					}
					if (axis.getDescription()!=null) {
						html.append("&nbsp;"+axis.getDescription());
					}
					html.append("<br>");
				}
			} catch (Exception e) {
				// ignore
			}
		}
		html.append("</div>");
		html.append("<div id=\"metrics\" class=\"tab-pane fade\">");
		//html.append("<div style='max-height:300px;overflow:scroll;'>");
		for (Measure m : space.M()) {
			if (m.getMetric()!=null && !m.getMetric().isDynamic()) {
				html.append("<span draggable='true'  style='"+metric_style+"'");
				ExpressionAST expr = m.getDefinitionSafe();
				html.append(" title='"+getExpressionValueType(expr).toString()+": ");
				if (m.getDescription()!=null) {
					html.append(m.getDescription());
				}
				html.append("'");
				html.append(" ondragstart='drag(event,\""+m.getName()+"\")'");
				html.append(">&nbsp;"+m.getName()+"&nbsp;</span><br>");
			}
		}
		html.append("</div>");
		// -- functions
		html.append("<div id=\"functions\" class=\"tab-pane fade\">");// style='max-height:600px;overflow:scroll;'>");
		try {
			List<OperatorDefinition> ops = new ArrayList<>(OperatorScope.getDefault().looseLookupByName(""));
			Collections.sort(ops, new Comparator<OperatorDefinition>() {
				@Override
				public int compare(OperatorDefinition o1, OperatorDefinition o2) {
					return o1.getName().compareTo(o2.getName());
				}
			});
            for (OperatorDefinition opDef : ops) {
            	if (opDef.getPosition()!=OperatorDefinition.INFIX_POSITION) {
                    List<List> poly = opDef.getParametersTypes();
                    ListContentAssistEntry listContentAssistEntry = opDef.getListContentAssistEntry();
                    if (listContentAssistEntry != null) {
                        if (listContentAssistEntry.getContentAssistEntries() != null) {
                            for (ContentAssistEntry contentAssistEntry : listContentAssistEntry.getContentAssistEntries()) {
                                //TODO this code should disappear when we get to XTEXT
                            	String function = opDef.getSymbol() + "(" + contentAssistEntry.getLabel() + ")";
                            	html.append("<span draggable='true'  style='"+metric_style+"'");
                            	html.append(" ondragstart='drag(event,\""+function+"\")'");
                				html.append(">&nbsp;"+function+"&nbsp;</span><br>");
                            }
                        }
                    }
            	}
            }
		} catch (ScopeException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		html.append("</div>");
		//---- container
		html.append("</div>");
	}
	
	/**
	 * @param space
	 * @param suggestions
	 * @param values 
	 * @param types 
	 * @param expression 
	 * @return
	 */
	public Response createHTMLPageScope(Space space, ExpressionSuggestion suggestions, String BBID, String value, ObjectType[] types, ValueType[] values) {
		String title = getPageTitle(space);
		StringBuilder html = createHTMLHeader("Scope: "+title);
		createHTMLtitle(html, title, BBID, getParentLink(space),null);
		if (value!=null && value.length()>0 && suggestions.getValidateMessage()!=null && suggestions.getValidateMessage().length()>0) {
			createHTMLproblems(html, Collections.singletonList(new Problem(Severity.WARNING, value, suggestions.getValidateMessage())));
		}
		html.append("<form>");
		html.append("<p>Expression:<input type='text' name='value' size=100 value='"+getFieldValue(value)+"' placeholder='type expression to validate it or to filter the suggestion list'></p>");
		html.append("<fieldset><legend>Filter by expression type</legend>");
		html.append("<input type='checkbox' name='types' value='"+ObjectType.DIMENSION+"'>"+ObjectType.DIMENSION);
		html.append("<input type='checkbox' name='types' value='"+ObjectType.COLUMN+"'>"+ObjectType.COLUMN);
		html.append("<input type='checkbox' name='types' value='"+ObjectType.RELATION+"'>"+ObjectType.RELATION);
		html.append("<input type='checkbox' name='types' value='"+ObjectType.METRIC+"'>"+ObjectType.METRIC);
		html.append("<input type='checkbox' name='types' value='"+ObjectType.FUNCTION+"'>"+ObjectType.FUNCTION);
		html.append("</fieldset>");
		html.append("<fieldset><legend>Filter by expression value</legend>");
		html.append("<input type='checkbox' name='values' value='"+ValueType.DATE+"'>"+ValueType.DATE);
		html.append("<input type='checkbox' name='values' value='"+ValueType.STRING+"'>"+ValueType.STRING);
		html.append("<input type='checkbox' name='values' value='"+ValueType.CONDITION+"'>"+ValueType.CONDITION);
		html.append("<input type='checkbox' name='values' value='"+ValueType.NUMERIC+"'>"+ValueType.NUMERIC);
		html.append("<input type='checkbox' name='values' value='"+ValueType.AGGREGATE+"'>"+ValueType.AGGREGATE);
		html.append("</fieldset>");
		html.append("<input type=\"hidden\" name=\"style\" value=\"HTML\">"
		+ "<input type=\"hidden\" name=\"access_token\" value=\""+space.getUniverse().getContext().getToken().getOid()+"\">"
		+ "<input type=\"submit\" value=\"Refresh\">");
		html.append("</form>");
		html.append("<p><i> This is the list of all available expressions and function in this scope. Relation expression can be composed in order to navigate the data model.</i></p>");
		html.append("<table>");
		for (ExpressionSuggestionItem item : suggestions.getSuggestions()) {
			html.append("<tr><td>");
			html.append(item.getObjectType()+"</td><td>");
			html.append(item.getValueType()+"</td><td>");
			String style = other_style;
			if (item.getObjectType()==ObjectType.DIMENSION) style = axis_style;
			if (item.getObjectType()==ObjectType.METRIC) style = metric_style;
			if (item.getObjectType()==ObjectType.FUNCTION) style = func_style;
			html.append("<span style='"+style+"'>&nbsp;"+item.getDisplay()+"&nbsp;</span>");
			if (item.getSuggestion()!=null) {
				URI link = service.getPublicBaseUriBuilder().path("/analytics/{reference}/scope").queryParam("value", value+item.getSuggestion()).queryParam("style", Style.HTML).queryParam("access_token", getToken()).build(BBID);
				html.append("&nbsp;[<a href=\""+StringEscapeUtils.escapeHtml4(link.toASCIIString())+"\">+</a>]");
			}
			if (item.getExpression()!=null && item.getExpression() instanceof AxisExpression) {
				AxisExpression ref = (AxisExpression)item.getExpression();
				Axis axis = ref.getAxis();
				if (axis.getDimensionType()==Type.CATEGORICAL) {
					URI link = service.getPublicBaseUriBuilder().path("/analytics/{reference}/facets/{facetId}").queryParam("style", Style.HTML).queryParam("access_token", getToken()).build(BBID, item.getSuggestion());
					html.append("&nbsp;[<a href=\""+StringEscapeUtils.escapeHtml4(link.toASCIIString())+"\">Indexed</a>]");
				} else if (axis.getDimensionType()==Type.CONTINUOUS) {
					URI link = service.getPublicBaseUriBuilder().path("/analytics/{reference}/facets/{facetId}").queryParam("style", Style.HTML).queryParam("access_token", getToken()).build(BBID, item.getSuggestion());
					html.append("&nbsp;[<a href=\""+StringEscapeUtils.escapeHtml4(link.toASCIIString())+"\">Period</a>]");
				}
			}
			if (item.getDescription()!=null && item.getDescription().length()>0) {
				html.append("<br><i>"+item.getDescription()+"</i>");
			}
			html.append("</td></tr>");
		}
		html.append("</table>");
		createHTMLAPIpanel(html, "scopeAnalysis");
		html.append("</body></html>");
		return Response.ok(html.toString(),"text/html").build();
	}

	private void createHTMLAPIpanel(StringBuilder html, String method) {
		html.append("<div style='clear:both;padding-top:30px;'>");
		html.append("<h4 style='font-family:Helvetica Neue,Helvetica,Arial,sans-serif;'>Query Reference</h4><hr>");
		// compute the raw URI
		UriBuilder builder = service.getPublicBaseUriBuilder().path(service.getUriInfo().getPath());
		MultivaluedMap<String, String> parameters = service.getUriInfo().getQueryParameters();
		//parameters.remove(ACCESS_TOKEN_PARAM);
		parameters.remove(STYLE_PARAM);
		parameters.remove(ENVELOPE_PARAM);
		for (Entry<String, List<String>> parameter : parameters.entrySet()) {
			for (String value : parameter.getValue()) {
				if (value!=null && !value.equals("")) {
					builder.queryParam(parameter.getKey(), value);
				}
			}
		}
		html.append("<p>Request URL: <i>the URL is authorized with the current token</i></p><div style='display:block;max-width: 999%; overflow-y: auto;'><pre>"+StringEscapeUtils.escapeHtml4(builder.build().toString())+"</pre></div>");
		String curlURL = "\""+(StringEscapeUtils.escapeHtml4(builder.build().toString()).replace("'", "'"))+"\"";
		html.append("<p>CURL: <i>the command is authorized with the current token</i></p><div style='display:block;max-width: 999%; overflow-y: auto;'><pre>curl -X GET --header 'Accept: application/json' --header 'Authorization: Bearer "+getToken()+"' "+curlURL+"</pre></div>");
		html.append("</div>");
		html.append("<div class=\"footer\"><p>Powered by <a href=\"http://openbouquet.io/\">Open Bouquet</a> <i style='color:white;'>the Analytics Rest API</i></p></div>\n");
		html.append("\n" + 
				"<script>\n" + 
				"$(document).ready(function(){\n" + 
				"    $('[data-toggle=\"tooltip\"]').tooltip();   \n" + 
				"});\n" + 
				"</script>");
	}
	
	private void createHTMLswaggerLink(StringBuilder html, String method) {
		String baseUrl = "";
		try {
			baseUrl = "?url="+URLEncoder.encode(service.getPublicBaseUriBuilder().path("swagger.json").build().toString(),"UTF-8")+"";
		} catch (UnsupportedEncodingException | IllegalArgumentException | UriBuilderException e) {
			// default
		}
		html.append("<p>the OB Analytics API provides more parameters... check in <a target='swagger' href='http://swagger.squidsolutions.com/"+baseUrl+"#!/analytics/"+method+"'>swagger UI</a> for details</p>");
	}
	
	/**
	 * displays query problems
	 * @param html
	 * @param query
	 */
	private void createHTMLproblems(StringBuilder html, List<Problem> problems) {
		if (problems!=null && problems.size()>0) {
			html.append("<div class='problems' style='border:1px solid red; background-color:lightpink;'>There are some problems with the query:");
			for (Problem problem : problems) {
				html.append("<li>"+problem.getSeverity().toString()+": "+problem.getSubject()+": "+problem.getMessage()+"</li>");
			}
			html.append("</div>");
		}
	}

	private String getPageTitle(Space space) {
		if (space.hasBookmark()) {
			String path = getBookmarkNavigationPath(space.getBookmark());
			return path+"/"+space.getBookmark().getName();
		} else {
			return "/PROJECTS/"+space.getUniverse().getProject().getName()+"/"+space.getDomain().getName();
		}
	}
	
	private String getDate(List<String> dates, int pos) {
		if (dates!=null && !dates.isEmpty() && pos<dates.size()) {
			return formatDateForWeb(getFieldValue(dates.get(pos)));
		} else {
			return "";
		}
	}

	private ValueType getExpressionValueType(ExpressionAST expr) {
		IDomain image = expr.getImageDomain();
		return ExpressionSuggestionHandler.computeValueTypeFromImage(image);
	}

	
	private SimpleDateFormat htmlDateFormat = new SimpleDateFormat("yyyy-MM-dd");
	
	private String formatDateForWeb(String jsonFormat) {
		try {
			Date date = ServiceUtils.getInstance().toDate(jsonFormat);
			return htmlDateFormat.format(date);
		} catch (ScopeException e) {
			return jsonFormat;
		}
	}
	
	/**
	 * @param result
	 * @return
	 */
	private String writeVegalightSpecs(VegaliteSpecs specs) {
		ObjectMapper mapper = new ObjectMapper();
		try {
			mapper.setSerializationInclusion(Include.NON_NULL);
			return mapper.writeValueAsString(specs);
		} catch (JsonProcessingException e) {
			throw new APIException("failed to write vegalite specs to JSON", e, true);
		}
	}
	
	private String getFieldValue(Object var) {
		if (var==null) return ""; else return var.toString().replaceAll("\"", "&quot;").replaceAll("'", "&#x27;");
	}
	
	private String getFieldValue(Object var, Object defaultValue) {
		if (var==null) return defaultValue.toString(); else return var.toString().replaceAll("\"", "&quot;").replaceAll("'", "&#x27;");
	}

	protected URI getParentLink(Space space) {
		if (space==null) return null;
		if (space.hasBookmark()) {
			String path = getBookmarkNavigationPath(space.getBookmark());
			return service.getPublicBaseUriBuilder().path("/analytics").queryParam(PARENT_PARAM, path).queryParam(STYLE_PARAM, "HTML").queryParam("access_token", getToken()).build();
		} else {
			return service.getPublicBaseUriBuilder().path("/analytics").queryParam(PARENT_PARAM, "/PROJECTS/"+space.getUniverse().getProject().getName()).queryParam(STYLE_PARAM, "HTML").queryParam("access_token", getToken()).build();
		}
	}
	
	public String getBookmarkNavigationPath(Bookmark bookmark) {
		String path = bookmark.getPath();
		if (path.startsWith(Bookmark.SEPARATOR + Bookmark.Folder.USER)) {
			String[] elements = path.split(Bookmark.SEPARATOR);
			if (elements.length>=3) {
				String oid = elements[2];
				path = path.substring((Bookmark.SEPARATOR + Bookmark.Folder.USER + Bookmark.SEPARATOR + oid).length());
				if (oid.equals(service.getUserContext().getUser().getOid())) {
					path = AnalyticsServiceBaseImpl.MYBOOKMARKS_FOLDER.getSelfRef()+path;
				} else {
					path = AnalyticsServiceBaseImpl.SHAREDWITHME_FOLDER.getSelfRef()+path;
				}
			} else {
				path = AnalyticsServiceBaseImpl.MYBOOKMARKS_FOLDER.getSelfRef()+path.substring((Bookmark.SEPARATOR + Bookmark.Folder.USER).length());
			}
		} else if (path.startsWith(Bookmark.SEPARATOR + Bookmark.Folder.SHARED)) {
			path = AnalyticsServiceBaseImpl.SHARED_FOLDER.getSelfRef()+path.substring((Bookmark.SEPARATOR + Bookmark.Folder.SHARED).length());
		}
		if (path.endsWith("/")) path = path.substring(0, path.length()-1);
		return path;
	}
	
}
