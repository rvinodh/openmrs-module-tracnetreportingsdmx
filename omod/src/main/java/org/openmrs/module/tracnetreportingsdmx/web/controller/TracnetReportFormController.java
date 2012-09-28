package org.openmrs.module.tracnetreportingsdmx.web.controller;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Location;
import org.openmrs.api.context.Context;
import org.openmrs.module.reporting.common.DateUtil;
import org.openmrs.module.reporting.evaluation.parameter.Mapped;
import org.openmrs.module.reporting.evaluation.parameter.ParameterException;
import org.openmrs.module.reporting.report.ReportRequest;
import org.openmrs.module.reporting.report.ReportRequest.Priority;
import org.openmrs.module.reporting.report.definition.ReportDefinition;
import org.openmrs.module.reporting.report.renderer.RenderingMode;
import org.openmrs.module.reporting.report.service.ReportService;
import org.openmrs.module.sdmxhdintegration.reporting.extension.SDMXHDCrossSectionalReportRenderer;
import org.openmrs.module.tracnetreportingsdmx.TracnetReport;
import org.openmrs.web.WebConstants;
import org.springframework.beans.propertyeditors.CustomDateEditor;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

@Controller
@RequestMapping("/module/tracnetreportingsdmx/tracnetReport")
public class TracnetReportFormController {

	/* Logger */
	private static Log log = LogFactory.getLog(TracnetReportFormController.class);
	
	/**
	 * Allows us to bind a custom editor for a class.
	 * @param binder
	 */
    @InitBinder
    public void initBinder(WebDataBinder binder) { 
    	binder.registerCustomEditor(Date.class, new CustomDateEditor(Context.getDateFormat(), true));
    }
    
    public Location getLocation() {
    	Location location = null;
		String locationId = Context.getAdministrationService().getGlobalProperty("tracnetreportingsdmx.locationId");
		if (StringUtils.isNotBlank(locationId)) {
			location = Context.getLocationService().getLocation(Integer.parseInt(locationId));
		}
		return location;
    }
    
    public RenderingMode getRenderingMode(ReportDefinition reportDefinition) {
		for (RenderingMode m : Context.getService(ReportService.class).getRenderingModes(reportDefinition)) {
			if (m.getRenderer() instanceof SDMXHDCrossSectionalReportRenderer) {
				return m;
			}
		}
		return null;
    }
	
    /**
     * Shows the form.  This method is called after the formBackingObject()
     * method below.
     * 
     * @return	the form model and view
     */
	@RequestMapping(method = RequestMethod.GET)
	public ModelAndView setupForm(HttpServletRequest request, @ModelAttribute("reportDefinition") ReportDefinition reportDefinition) {
		request.getSession().removeAttribute("reportData");
		ModelAndView model = new ModelAndView("/module/tracnetreportingsdmx/tracnetReportForm");
		model.addObject("location", getLocation());

		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.MONTH, -1);
		model.addObject("year", Integer.valueOf(cal.get(Calendar.YEAR)));
		model.addObject("month", cal.get(Calendar.MONTH));
		model.addObject("format", getRenderingMode(reportDefinition));
		Map<Integer, String> months = new LinkedHashMap<Integer, String>();
		for (int i=1; i<=12; i++) {
			cal.set(Calendar.MONTH, i-1);
			months.put(Integer.valueOf(i), cal.getDisplayName(Calendar.MONDAY, Calendar.LONG, Context.getLocale()));
		}
		model.addObject("months", months);
		return model; 
	}	
	
	/**
	 * Processes the form when a user submits.  
	 * 
	 * @param cohortDefinition
	 * @param bindingResult
	 * @return
	 */	
	@RequestMapping(method = RequestMethod.POST)
	public ModelAndView processForm(
			HttpServletRequest request,	
			HttpServletResponse response,	
			@RequestParam(value = "year", required=true) Integer year,
			@RequestParam(value = "month", required=true) Integer month,	
			@ModelAttribute("reportDefinition") ReportDefinition reportDefinition, 
			BindingResult bindingResult) throws Exception {
					
		log.info("Report definition: " + reportDefinition);
		
		if (reportDefinition != null) {			
			try {
				
				Date startDate = DateUtil.getDateTime(year, month, 1);
				Date endDate = DateUtil.getEndOfMonth(startDate);
				
				Map<String, Object> parameterValues = new HashMap<String, Object>();
				parameterValues.put("startDate", startDate);
				parameterValues.put("endDate", endDate);
				parameterValues.put("location", getLocation());
				
				ReportRequest rr = new ReportRequest();
				rr.setReportDefinition(new Mapped<ReportDefinition>(reportDefinition, parameterValues));
				rr.setPriority(Priority.HIGHEST);
				rr.setRequestDate(new Date());
				rr.setRequestedBy(Context.getAuthenticatedUser());
				rr.setRenderingMode(getRenderingMode(reportDefinition));
				Context.getService(ReportService.class).queueReport(rr);
				
				String url = request.getContextPath() + "/module/reporting/reports/reportHistoryOpen.form?uuid=" + rr.getUuid();
				return new ModelAndView(new RedirectView(url));				
			} 
			catch(ParameterException e) { 
				log.error("unable to evaluate report: ", e);
				request.getSession().setAttribute(WebConstants.OPENMRS_ERROR_ATTR, "Unable to evaluate report: " + e.getMessage());
				setupForm(request, reportDefinition);
			}			
		}
		return new ModelAndView();
	}

	@ModelAttribute("reportDefinition")
	public ReportDefinition formBackingObject() {
		return TracnetReport.getTracnetReportDefinition(false);
	}
	
}
