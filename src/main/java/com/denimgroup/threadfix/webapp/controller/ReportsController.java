////////////////////////////////////////////////////////////////////////
//
//     Copyright (c) 2009-2013 Denim Group, Ltd.
//
//     The contents of this file are subject to the Mozilla Public License
//     Version 2.0 (the "License"); you may not use this file except in
//     compliance with the License. You may obtain a copy of the License at
//     http://www.mozilla.org/MPL/
//
//     Software distributed under the License is distributed on an "AS IS"
//     basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
//     License for the specific language governing rights and limitations
//     under the License.
//
//     The Original Code is ThreadFix.
//
//     The Initial Developer of the Original Code is Denim Group, Ltd.
//     Portions created by Denim Group, Ltd. are Copyright (C)
//     Denim Group, Ltd. All Rights Reserved.
//
//     Contributor(s): Denim Group, Ltd.
//
////////////////////////////////////////////////////////////////////////
package com.denimgroup.threadfix.webapp.controller;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.support.SessionStatus;

import com.denimgroup.threadfix.data.entities.Application;
import com.denimgroup.threadfix.data.entities.Organization;
import com.denimgroup.threadfix.data.entities.ReportParameters;
import com.denimgroup.threadfix.service.OrganizationService;
import com.denimgroup.threadfix.service.PermissionService;
import com.denimgroup.threadfix.service.SanitizedLogger;
import com.denimgroup.threadfix.service.report.ReportsService;
import com.denimgroup.threadfix.service.report.ReportsService.ReportCheckResult;

@Controller
@RequestMapping("/reports")
@PreAuthorize("hasRole('ROLE_CAN_GENERATE_REPORTS')")
public class ReportsController {
	
	private final SanitizedLogger log = new SanitizedLogger(ReportsController.class);

	private OrganizationService organizationService;
	private PermissionService permissionService;
	private ReportsService reportsService;
	
	@Autowired
	public ReportsController(OrganizationService organizationService,
			PermissionService permissionService,
			ReportsService reportsService) {
		this.organizationService = organizationService;
		this.permissionService = permissionService;
		this.reportsService = reportsService;
	}

	public ReportsController(){}
	
	@ModelAttribute("organizationList")
	public List<Organization> getOrganizations() {
		List<Organization> organizationList = organizationService.loadAllActiveFilter();
		List<Organization> returnList = new ArrayList<Organization>();

		for (Organization org : organizationList) {
			List<Application> validApps = permissionService.filterApps(org);
			if (validApps != null && !validApps.isEmpty()) {
				org.setActiveApplications(validApps);
				returnList.add(org);
			}
		}
		return returnList;
	}

	@RequestMapping(method = RequestMethod.GET)
	public String index(Model model, HttpServletRequest request) {
		model.addAttribute("reportParameters", new ReportParameters());
		model.addAttribute("error", ControllerUtils.getErrorMessage(request));
		return "reports/index";
	}
	
	@RequestMapping(value="/ajax/export", method = RequestMethod.POST)
	public String processExportRequest(Model model, @ModelAttribute ReportParameters reportParameters,
			BindingResult result, SessionStatus status, HttpServletRequest request, 
			HttpServletResponse response) throws IOException {
		
		ReportCheckResultBean reportCheckResultBean = reportsService.generateReport(reportParameters,
				request, response);
		
		ReportCheckResult reportCheckResult = reportCheckResultBean.getReportCheckResult();
		
		if (reportCheckResult == ReportCheckResult.VALID) {
			StringBuffer report = reportCheckResultBean.getReport();
			String pageString = report.toString();
			response.setContentType("application/octet-stream");
			
			String fileName = reportParameters.getFormatId() == 2 ? "report_csv.csv" : "report_pdf.pdf";
			response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");

			ServletOutputStream out = response.getOutputStream();

			InputStream in = new ByteArrayInputStream(pageString.getBytes("UTF-8"));

			byte[] outputByte = new byte[65535];
			
			int remainingSize = in.read(outputByte, 0, 65535);
			
			// copy binary content to output stream
			while (remainingSize != -1) {
				out.write(outputByte, 0, remainingSize);
				remainingSize = in.read(outputByte, 0, 65535);
			}
			in.close();
			out.flush();
			out.close();
			return null;
		} else {
			return returnError(request, model, reportCheckResult);
		}
	}

	@RequestMapping(value="/ajax", method = RequestMethod.POST)
	public String processSubmit(Model model, @ModelAttribute ReportParameters reportParameters,
			BindingResult result, SessionStatus status, HttpServletRequest request, 
			HttpServletResponse response) throws IOException {
		
		// reroute if it's scanner comparison or portfolio report
		if (reportParameters.getReportId() == 6) {
			return reportsService.scannerComparisonByVulnerability(model, reportParameters);
		} else if (reportParameters.getReportId() == 8) {
			return new PortfolioReportController(organizationService).index(
					model, request, reportParameters.getOrganizationId());
		}
		
		ReportCheckResultBean reportCheckResultBean = reportsService.generateReport(reportParameters,
				request, response);
		
		ReportCheckResult reportCheckResult = reportCheckResultBean.getReportCheckResult();
		
		if (reportCheckResult == ReportCheckResult.VALID) {
			boolean csvEnabled = !(reportParameters.getReportId() == 1 || reportParameters.getReportId() == 7);
			StringBuffer report = reportCheckResultBean.getReport();
			
			if (report != null) {
				log.info("Finished generating report.");
				model.addAttribute("jasperReport", addParameterToReport(report));
				model.addAttribute("csvEnabled", csvEnabled);
				model.addAttribute("reportId",reportParameters.getReportId());
				return "reports/report";
				
			} else {
				log.warn("Failed to generate report.");
				ControllerUtils.addErrorMessage(request, "There was an error generating the report.");
				model.addAttribute("contentPage", "/reports");
				return "ajaxRedirectHarness";
			}
		} else {
			return returnError(request, model, reportCheckResult);
		}
	}
	
	private String addParameterToReport(StringBuffer buffer) {
		String resultString = buffer.toString();
		String regex = "(.*<img [^>]*img_[^\"]*)(.*)";
		return resultString.replaceAll(regex, "$1?do_it" + new Random().nextInt() + "$2");
	}
	
	private String returnError(HttpServletRequest request, Model model,
			ReportCheckResult reportCheckResult) {
		if (reportCheckResult == ReportCheckResult.BAD_REPORT_TYPE) {
			return incorrectReportIdError(request, model);
		} else if (reportCheckResult == ReportCheckResult.NO_APPLICATIONS) {
			return missingApplicationsError(request, model);
		} else {
			return exceptionError(request, model);
		}
	}
	
	private String incorrectReportIdError(HttpServletRequest request, Model model) {
		log.warn("An incorrect report ID was passed through, returning an error page.");
		ControllerUtils.addErrorMessage(request, "An invalid report type was chosen.");
		return redirect(model);
	}
	
	private String missingApplicationsError(HttpServletRequest request, Model model) {
		ControllerUtils.addErrorMessage(request, "You must select at least one application.");
		return redirect(model);
	}
	
	private String exceptionError(HttpServletRequest request, Model model) {
		ControllerUtils.addErrorMessage(request, "An error occurred while generating the report. " +
				"Check the logs for more details.");
		return redirect(model);
	}
	
	private String redirect(Model model) {
		model.addAttribute("contentPage", "/reports");
		return "ajaxRedirectHarness";
	}
	
}