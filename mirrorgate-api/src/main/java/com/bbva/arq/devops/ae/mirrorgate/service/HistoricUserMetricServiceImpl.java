package com.bbva.arq.devops.ae.mirrorgate.service;

import static com.bbva.arq.devops.ae.mirrorgate.mapper.HistoricUserMetricMapper.mapToHistoric;

import com.bbva.arq.devops.ae.mirrorgate.core.dto.DashboardDTO;
import com.bbva.arq.devops.ae.mirrorgate.dto.HistoricTendenciesDTO;
import com.bbva.arq.devops.ae.mirrorgate.model.HistoricUserMetric;
import com.bbva.arq.devops.ae.mirrorgate.model.UserMetric;
import com.bbva.arq.devops.ae.mirrorgate.repository.HistoricUserMetricRepository;
import com.bbva.arq.devops.ae.mirrorgate.utils.LocalDateTimeHelper;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;


@Service
public class HistoricUserMetricServiceImpl implements HistoricUserMetricService {

    private static final Logger LOGGER = LoggerFactory.getLogger(HistoricUserMetricServiceImpl.class);
    private static final int MAX_NUMBER_OF_DAYS_TO_STORE = 90;
    private static final int MAX_NUMBER_OF_MINUTES_TO_STORE = 150;
    private static final int LONG_TERM_TENDENCY_LONG_PERIOD = 720; //30 days in hours
    private static final int LONG_TERM_TENDENCY_SHORT_PERIOD = 96; //4 days in hours

    private final HistoricUserMetricRepository historicUserMetricRepository;

    @Autowired
    public HistoricUserMetricServiceImpl(HistoricUserMetricRepository historicUserMetricRepository){

        this.historicUserMetricRepository = historicUserMetricRepository;
    }


    @Override
    public void addToCurrentPeriod(Iterable<UserMetric> saved) {

        saved.forEach( s -> {
            try {
               addToShortTermTendency(s);
               addToLongTermTendency(s);
            } catch (Exception e) {
                LOGGER.error("Error while processing metric : {}", s.getName(), e);
            }
        });
    }

    @Override
    public void removeExtraPeriodsForMetricAndIdentifier(String metricName, String identifier, ChronoUnit unit, long timestamp) {

        LOGGER.debug("removing extra periods for: {}, {}, {}", metricName, identifier, timestamp);

        List<HistoricUserMetric> oldPeriods = historicUserMetricRepository.findByNameAndIdentifierAndHistoricTypeAndTimestampLessThan(metricName, identifier, unit, timestamp);

        historicUserMetricRepository.delete(oldPeriods);
    }

    @Override
    public HistoricTendenciesDTO getHistoricMetricsForDashboard(DashboardDTO dashboard, String metricName) {
        List<String> views = dashboard.getAnalyticViews();

        HistoricTendenciesDTO tendencies = new HistoricTendenciesDTO();

        if (views == null || views.isEmpty()) {
            return tendencies;
        }

        tendencies.setLongTermTendency(calculateLongTermTendency(views, metricName));
        tendencies.setShortTermTendency(calculateShortTermTendency());

        return tendencies;
    }

    @Override
    public HistoricUserMetric getHistoricMetricForPeriod(long periodTimestamp, String identifier, ChronoUnit type) {

        return historicUserMetricRepository.findByTimestampAndIdentifierAndHistoricType(periodTimestamp, identifier, type);
    }

    private void addToShortTermTendency(UserMetric userMetric){
        HistoricUserMetric metric = addToTendency(userMetric, ChronoUnit.MINUTES);

        removeExtraPeriodsForMetricAndIdentifier( metric.getName(), metric.getIdentifier(),
            ChronoUnit.MINUTES, LocalDateTimeHelper.getTimestampForNMinutesAgo(MAX_NUMBER_OF_MINUTES_TO_STORE, ChronoUnit.MINUTES));
    }

    private void addToLongTermTendency(UserMetric userMetric){
        HistoricUserMetric metric = addToTendency(userMetric, ChronoUnit.HOURS);

        removeExtraPeriodsForMetricAndIdentifier( metric.getName(), metric.getIdentifier(),
            ChronoUnit.HOURS, LocalDateTimeHelper.getTimestampForNDaysAgo(MAX_NUMBER_OF_DAYS_TO_STORE, ChronoUnit.HOURS));
    }


    private HistoricUserMetric addToTendency(UserMetric userMetric, ChronoUnit unit){

        HistoricUserMetric metric = getHistoricMetricForPeriod(
            LocalDateTimeHelper.getTimestampPeriod(userMetric.getTimestamp(), unit),
            userMetric.getId(), unit);

        if (metric == null) {
            metric = createNextPeriod(userMetric, unit);
        }

        HistoricUserMetric addedMetric = addMetrics(metric, userMetric);
        historicUserMetricRepository.save(addedMetric);

        return metric;
    }

    private HistoricUserMetric addMetrics (final HistoricUserMetric historic, final UserMetric saved){

        HistoricUserMetric response =  historic;

        if(saved.getSampleSize() != null){
            double value = (historic.getValue()*historic.getSampleSize()+saved.getValue()*saved.getSampleSize())/(historic.getSampleSize()+saved.getSampleSize());
            response.setValue(value);
            response.setSampleSize(response.getSampleSize()+saved.getSampleSize());
        } else {
            response.setValue(response.getValue() + saved.getValue());
        }

        return response;
    }

    private HistoricUserMetric createNextPeriod(UserMetric userMetric, ChronoUnit unit) {

        LOGGER.debug("creating new Historic Metric Period");

        HistoricUserMetric historicUserMetric = mapToHistoric(userMetric);

        historicUserMetric.setSampleSize(0d);
        historicUserMetric.setTimestamp(LocalDateTimeHelper.getTimestampPeriod(userMetric.getTimestamp(), unit));
        historicUserMetric.setValue(0d);
        historicUserMetric.setHistoricType(unit);

        return historicUserMetric;
    }

    private double calculateLongTermTendency(List<String> views, String metricName){

        List<HistoricUserMetric> historicUserMetrics =
            historicUserMetricRepository.findAllByViewIdInAndValueGreaterThanAndNameAndHistoricTypeOrderByTimestampAsc
            (new PageRequest(0, LONG_TERM_TENDENCY_LONG_PERIOD), views, 0d, metricName, ChronoUnit.HOURS);

        double fourDaysAverage = getAverageValue(historicUserMetrics.subList(0, LONG_TERM_TENDENCY_SHORT_PERIOD));
        double thirtyDaysAverage = getAverageValue(historicUserMetrics);

        return getPercentualDifference(thirtyDaysAverage, fourDaysAverage);
    }

    //TODO
    private long calculateShortTermTendency(){
        return 0L;
    }

    private double getAverageValue(List<HistoricUserMetric> historicUserMetrics){
        return historicUserMetrics.stream()
            .mapToDouble(HistoricUserMetric::getValue)
            .sum()/historicUserMetrics.size();
    }

    private double getPercentualDifference(double longPeriod, double shortPeriod){

        return ((shortPeriod - longPeriod)/longPeriod) * 100;
    }
}
