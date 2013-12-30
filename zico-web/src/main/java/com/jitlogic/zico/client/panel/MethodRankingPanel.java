/**
 * Copyright 2012-2013 Rafal Lewczuk <rafal.lewczuk@jitlogic.com>
 * <p/>
 * This is free software. You can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * <p/>
 * This software is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p/>
 * You should have received a copy of the GNU General Public License
 * along with this software. If not, see <http://www.gnu.org/licenses/>.
 */
package com.jitlogic.zico.client.panel;


import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.inject.assistedinject.Assisted;
import com.google.web.bindery.requestfactory.shared.Receiver;
import com.google.web.bindery.requestfactory.shared.ServerFailure;
import com.jitlogic.zico.client.ErrorHandler;
import com.jitlogic.zico.client.inject.ZicoRequestFactory;
import com.jitlogic.zico.client.props.MethodRankInfoProperties;
import com.jitlogic.zico.shared.data.MethodRankInfoProxy;
import com.jitlogic.zico.shared.data.TraceInfoProxy;
import com.sencha.gxt.data.shared.ListStore;
import com.sencha.gxt.widget.core.client.container.VerticalLayoutContainer;
import com.sencha.gxt.widget.core.client.grid.ColumnConfig;
import com.sencha.gxt.widget.core.client.grid.ColumnModel;
import com.sencha.gxt.widget.core.client.grid.Grid;
import com.sencha.gxt.widget.core.client.grid.GridView;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.List;

public class MethodRankingPanel extends VerticalLayoutContainer {

    private static final MethodRankInfoProperties props = GWT.create(MethodRankInfoProperties.class);

    private ZicoRequestFactory rf;
    private TraceInfoProxy traceInfo;
    private ErrorHandler errorHandler;

    private Grid<MethodRankInfoProxy> rankGrid;
    private GridView<MethodRankInfoProxy> rankGridView;
    private ListStore<MethodRankInfoProxy> rankStore;

    private final int COL_SZ = 40;

    @Inject
    public MethodRankingPanel(ZicoRequestFactory rf, ErrorHandler errorHandler, @Assisted TraceInfoProxy traceInfo) {
        this.rf = rf;
        this.traceInfo = traceInfo;
        this.errorHandler = errorHandler;

        createRankingGrid();
        loadData("calls", "DESC");
    }

    private void createRankingGrid() {

        ColumnConfig<MethodRankInfoProxy, String> colMethod = new ColumnConfig<MethodRankInfoProxy, String>(props.method(), 500, "Method");
        colMethod.setMenuDisabled(true);

        ColumnConfig<MethodRankInfoProxy, Long> colCalls = new ColumnConfig<MethodRankInfoProxy, Long>(props.calls(), COL_SZ, "Calls");
        colCalls.setAlignment(HasHorizontalAlignment.ALIGN_CENTER);
        colCalls.setMenuDisabled(true);

        ColumnConfig<MethodRankInfoProxy, Long> colErrors = new ColumnConfig<MethodRankInfoProxy, Long>(props.errors(), COL_SZ, "Errors");
        colErrors.setAlignment(HasHorizontalAlignment.ALIGN_CENTER);
        colErrors.setMenuDisabled(true);

        ColumnConfig<MethodRankInfoProxy, Long> colTime = new ColumnConfig<MethodRankInfoProxy, Long>(props.time(), COL_SZ, "Time");
        colTime.setAlignment(HasHorizontalAlignment.ALIGN_CENTER);
        colTime.setToolTip(SafeHtmlUtils.fromString("Total execution time - sum of execution times of all method calls"));
        colTime.setCell(new NanoTimeRenderingCell());
        colTime.setMenuDisabled(true);

        ColumnConfig<MethodRankInfoProxy, Long> colMinTime = new ColumnConfig<MethodRankInfoProxy, Long>(props.minTime(), COL_SZ, "MinTime");
        colTime.setToolTip(SafeHtmlUtils.fromString("Peak execution time"));
        colMinTime.setCell(new NanoTimeRenderingCell());
        colMinTime.setAlignment(HasHorizontalAlignment.ALIGN_CENTER);
        colMinTime.setMenuDisabled(true);

        ColumnConfig<MethodRankInfoProxy, Long> colMaxTime = new ColumnConfig<MethodRankInfoProxy, Long>(props.maxTime(), COL_SZ, "MaxTime");
        colTime.setToolTip(SafeHtmlUtils.fromString("Peak execution time"));
        colMaxTime.setCell(new NanoTimeRenderingCell());
        colMaxTime.setAlignment(HasHorizontalAlignment.ALIGN_CENTER);
        colMaxTime.setMenuDisabled(true);

        ColumnConfig<MethodRankInfoProxy, Long> colAvgTime = new ColumnConfig<MethodRankInfoProxy, Long>(props.avgTime(), COL_SZ, "AvgTime");
        colTime.setToolTip(SafeHtmlUtils.fromString("Average execution time"));
        colAvgTime.setCell(new NanoTimeRenderingCell());
        colAvgTime.setAlignment(HasHorizontalAlignment.ALIGN_CENTER);
        colAvgTime.setMenuDisabled(true);

        ColumnConfig<MethodRankInfoProxy, Long> colBareTime = new ColumnConfig<MethodRankInfoProxy, Long>(props.bareTime(), COL_SZ, "BTime");
        colBareTime.setAlignment(HasHorizontalAlignment.ALIGN_CENTER);
        colBareTime.setToolTip(SafeHtmlUtils.fromString("Total bare execution time - with child methods time subtracted"));
        colBareTime.setCell(new NanoTimeRenderingCell());
        colBareTime.setMenuDisabled(true);

        ColumnConfig<MethodRankInfoProxy, Long> colMaxBareTime = new ColumnConfig<MethodRankInfoProxy, Long>(props.maxBareTime(), COL_SZ, "MaxBTime");
        colMaxBareTime.setAlignment(HasHorizontalAlignment.ALIGN_CENTER);
        colMaxBareTime.setToolTip(SafeHtmlUtils.fromString("Maximum bare execution time - with child methods time subtracted"));
        colMaxBareTime.setCell(new NanoTimeRenderingCell());
        colMaxBareTime.setMenuDisabled(true);

        ColumnConfig<MethodRankInfoProxy, Long> colAvgBareTime = new ColumnConfig<MethodRankInfoProxy, Long>(props.avgBareTime(), COL_SZ, "AvgBTime");
        colAvgBareTime.setAlignment(HasHorizontalAlignment.ALIGN_CENTER);
        colAvgBareTime.setToolTip(SafeHtmlUtils.fromString("Average bare execution time - with child methods time subtracted"));
        colAvgBareTime.setCell(new NanoTimeRenderingCell());
        colAvgBareTime.setMenuDisabled(true);

        ColumnModel<MethodRankInfoProxy> model = new ColumnModel<MethodRankInfoProxy>(Arrays.<ColumnConfig<MethodRankInfoProxy, ?>>asList(
                colCalls, colErrors, colTime, colMinTime, colMaxTime, colAvgTime, colBareTime, colAvgBareTime, colMethod
        ));

        rankStore = new ListStore<MethodRankInfoProxy>(props.key());
        rankGrid = new Grid<MethodRankInfoProxy>(rankStore, model);
        rankGridView = rankGrid.getView();

        rankGridView.setAutoExpandColumn(colMethod);
        rankGridView.setForceFit(true);

        add(rankGrid, new VerticalLayoutData(1, 1));
    }

    private void loadData(String orderBy, String orderDir) {
        rf.traceDataService().traceMethodRank(traceInfo.getHostName(), traceInfo.getDataOffs(), orderBy, orderDir).fire(
                new Receiver<List<MethodRankInfoProxy>>() {
                    @Override
                    public void onSuccess(List<MethodRankInfoProxy> ranking) {
                        rankStore.clear();
                        rankStore.addAll(ranking);
                    }
                    @Override
                    public void onFailure(ServerFailure error) {
                        errorHandler.error("Error loading method rank data", error);
                    }
                }
        );
    }
}
