import React, { Component, Fragment } from 'react';
import PropTypes from 'prop-types';
import { translate } from 'react-i18next'
import update from 'immutability-helper'
import {
    Row, Col, Checkbox, InputNumber, Tooltip,
    Select, Input, Collapse, Icon, Button, Avatar
} from 'antd';
import _ from 'lodash';
import resource from "./../../Resource";
import ParameterTable from './ParameterTable';
import NfDataGrid from './../NfDataGrid';
import { createHrefWithNewObject } from './../../utils/encode'

const Panel = Collapse.Panel

class ContextStep extends Component {

    static propTypes = {
        entity: PropTypes.object
    }

    constructor(...args) {
        super(...args);
        this.state = {
            jdbcContextList: [],
            oozieServerList: [],
            transformationList: []
        }
        this._isMounted = false
    }

    getOozieServerList() {
        resource.query('/api/teneo/rt.Oozie/').then(result => {
            this._isMounted && this.setState({ oozieServerList: result })
        })
    }

    getJdbcContextList() {
        resource.query('/api/teneo/etl.Context/').then(result => {
            this._isMounted && this.setState({ jdbcContextList: result })
        })
    }

    getProjectTransformationList() {
        resource.query('/api/teneo/select/select e from etl.Transformation e').then(result => {
            this._isMounted && this.setState({ transformationList: result })
        })
    }

    changeEntityValue(key, value) {
        value = value === "" ? null : value 
        const { updateEntity, entity } = this.props
        const updatedEntity = update(entity, {
            [key]: { $set: value }
        })
        updateEntity(updatedEntity)
    }

    handleDataChange = (object, targetKey, newData, ) => {
        const { updateEntity } = this.props
        updateEntity({ [targetKey]: newData })
    }

    componentWillUnmount() {
        this._isMounted = false
    }

    componentDidMount() {
        this._isMounted = true
        this.getOozieServerList()
        this.getJdbcContextList()
        this.getProjectTransformationList()
    }

    render() {
        const { jdbcContextList, oozieServerList, transformationList } = this.state
        const { t, entity } = this.props
        const { location } = window
        return (
            <Fragment>
                <br />
                <div className="pretty-box">
                    <Row>
                        <Col span={6}>
                            <div className="pretty-box-title" style={{ fontWeight: 'bold', top: '-45px' }}>
                                {t('rt.ImportWizard.views.contextandworkflow', { ns: ['classes'] })}
                            </div>
                            <div>{t('rt.ImportWizard.attrs.jdbcContext.caption', { ns: ['classes'] })}</div>
                            <Select
                                value={entity.jdbcContext ? entity.jdbcContext.name : []}
                                style={{ width: '69%', marginBottom: '10px' }}
                                onChange={(value) => {
                                    const jdbcContextEntity = jdbcContextList.find(o => o.name === value)
                                    this.changeEntityValue("jdbcContext", { ...jdbcContextEntity })
                                }}
                            >
                                {jdbcContextList.map(j =>
                                    <Select.Option key={j.name} value={j.name}>{j.name}</Select.Option>)}
                            </Select>
                            <div>{t('rt.ImportWizard.attrs.oozie.caption', { ns: ['classes'] })}</div>
                            <Select
                                value={entity.oozie ? entity.oozie.name : []}
                                style={{ width: '69%', marginBottom: '10px' }}
                                onChange={(value) => {
                                    const oozieEntity = oozieServerList.find(o => o.name === value)
                                    this.changeEntityValue("oozie", { ...oozieEntity })
                                }}
                            >
                                {oozieServerList.map(o =>
                                    <Select.Option key={o.name} value={o.name}>{o.name}</Select.Option>)}
                            </Select>
                            <div>{t('rt.ImportWizard.attrs.templateTransformation.caption', { ns: ['classes'] })}</div>
                            <Input.Group compact>
                                <Select
                                    allowClear
                                    value={entity.templateTransformation ? entity.templateTransformation.name : []}
                                    style={{ width: '60%', marginBottom: '10px' }}
                                    onChange={(value) => {
                                        const transformationEntity = transformationList.find(o => o.name === value)
                                        this.changeEntityValue("templateTransformation", { ...transformationEntity })
                                    }}
                                >
                                    {transformationList.map(t =>
                                        <Select.Option key={t.name} value={t.name}>{t.name}</Select.Option>)}
                                </Select>
                                <Tooltip placement="top" title={t("edit")}>
                                    <Button type="dashed" placement="">
                                        {<a onClick={(e) => { e.stopPropagation() }}
                                            target="_blank"
                                            href={
                                                entity.templateTransformation && createHrefWithNewObject(location, {
                                                    e_id: entity.templateTransformation.e_id,
                                                    _type_: entity.templateTransformation._type_,
                                                    name: entity.templateTransformation.name
                                                })
                                            }>
                                            <Avatar className='avatar-button-property' size='small' src='images/icon-core/edit-modern.svg' />
                                        </a>}
                                    </Button>
                                </Tooltip>
                            </Input.Group>
                            <div>{t('rt.ImportWizard.attrs.wfParallelism.caption', { ns: ['classes'] })}</div>
                            <InputNumber
                                value={entity.wfParallelism ? entity.wfParallelism : undefined}
                                onChange={(value) => this.changeEntityValue("wfParallelism", value)}
                                style={{ width: '30%', marginBottom: '10px' }}
                            />
                            <div>{t('rt.ImportWizard.attrs.hdfsPath.caption', { ns: ['classes'] })}</div>
                            <Input
                                value={entity.hdfsPath ? entity.hdfsPath : undefined}
                                onChange={(e) => this.changeEntityValue("hdfsPath", e.target.value)}
                                style={{ width: '95%', marginBottom: '10px' }}
                            />
                            <br />
                            <Checkbox
                                style={{ marginBottom: '10px' }}
                                checked={entity.registerHiveTable ? entity.registerHiveTable : false}
                                onChange={(e) => this.changeEntityValue("registerHiveTable", e.target.checked)}
                            >
                                {t('rt.ImportWizard.attrs.registerHiveTable.caption', { ns: ['classes'] })}
                            </Checkbox>
                            <br/>
                            <Checkbox
                                                            style={{ marginBottom: '10px' }}
                                                            checked={entity.exitOnFail ? entity.exitOnFail : false}
                                                            onChange={(e) => this.changeEntityValue("exitOnFail", e.target.checked)}
                                                        >
                                                            {t('rt.ImportWizard.attrs.exitOnFail.caption', { ns: ['classes'] })}
                                                        </Checkbox>
                            <div>{t('rt.ImportWizard.attrs.loggingTransformation.caption', { ns: ['classes'] })}</div>
                                                        <Input.Group compact>
                                                            <Select
                                                                allowClear
                                                                value={entity.loggingTransformation ? entity.loggingTransformation.name : []}
                                                                style={{ width: '60%', marginBottom: '10px' }}
                                                                onChange={(value) => {
                                                                    const transformationEntity = transformationList.find(o => o.name === value)
                                                                    this.changeEntityValue("loggingTransformation", { ...transformationEntity })
                                                                }}
                                                            >
                                                                {transformationList.map(t =>
                                                                    <Select.Option key={t.name} value={t.name}>{t.name}</Select.Option>)}
                                                            </Select>
                                                            <Tooltip placement="top" title={t("edit")}>
                                                                <Button type="dashed" placement="">
                                                                    {<a onClick={(e) => { e.stopPropagation() }}
                                                                        target="_blank"
                                                                        href={
                                                                            entity.loggingTransformation && createHrefWithNewObject(location, {
                                                                                e_id: entity.loggingTransformation.e_id,
                                                                                _type_: entity.loggingTransformation._type_,
                                                                                name: entity.loggingTransformation.name
                                                                            })
                                                                        }>
                                                                        <Avatar className='avatar-button-property' size='small' src='images/icon-core/edit-modern.svg' />
                                                                    </a>}
                                                                </Button>
                                                            </Tooltip>
                                                        </Input.Group>
                        </Col>
                        <Col span={18}>
                            <div style={{ marginBottom: '2px' }}>{t('rt.ImportWizard.attrs.workflowParameters.caption', { ns: ['classes'] })}</div>
                            <ParameterTable
                                parameters={entity.workflowParameters}
                                onDataChange={this.handleDataChange}
                                objectType='etl.Property'
                                targetKey='workflowParameters'
                            />
                        </Col>
                    </Row>
                </div>
            </Fragment>
        )
    }

}

class ObjectsStep extends Component {

    static propTypes = {
        entity: PropTypes.object
    }

    constructor(...args) {
        super(...args);
        this.schemaGrid = null
        this.isSorted = false
    }

    changeEntityValue(key, value) {
        const { updateEntity, entity } = this.props
        const updatedEntity = update(entity, {
            [key]: { $set: value }
        })
        updateEntity(updatedEntity)
    }

    handleSchemaGridClick = (grid) => {
        const { entity, updateEntity } = this.props
        let updatedList = _.cloneDeep(entity.entities)
        updatedList.forEach((row) => {
            if (row.schema === grid.data.schema && (row.importEntityType === 'TABLE' ? entity.showTables : entity.showViews) > 0) {
                row.active = grid.node.selected
            }
        })
        updateEntity({ 'entities': updatedList })
    }

    handleObjectGridClick = (cell) => {
        const { entity, updateEntity } = this.props
        const index = entity.entities.findIndex(e => cell.data.name === e.name && cell.data.schema === e.schema)
        const updatedList = update(entity.entities, { [index]: { active: { $set: cell.node.selected } } })
        updateEntity({ 'entities': updatedList })
    }

    selectActiveObjects() {
        const { entity } = this.props
        this.objectGrid.grid.current.api.forEachNode((node) => {
            const isSelected = entity.entities.findIndex(
                o => o.active === true && o.schema === node.data.schema && o.name === node.data.name)
            isSelected >= 0 && node.setSelected(true)
        })
    }

    selectActiveSchemas() {
        const { entity } = this.props
        this.schemaGrid.grid.current.api.forEachNode((node) => {
            const isSelected = entity.entities.findIndex(
                o => o.active === true && o.schema === node.data.schema)
            isSelected >= 0 && node.setSelected(true)
        })
    }

    selectFirstSchema() {
        if (this.schemaGrid.grid) {
            this.selectActiveSchemas()
            const rowAmount = this.schemaGrid.grid.current.api.getDisplayedRowCount()
            const selectedRowAmount = this.schemaGrid.grid.current.api.getSelectedRows().length
            if (rowAmount > 0 && selectedRowAmount === 0) {
                const firstRow = this.schemaGrid.grid.current.api.getRenderedNodes()[0]
                const cell = {
                    data: firstRow.data,
                    node: { selected: true }
                }
                this.handleSchemaGridClick(cell)
            }
        }
    }

    setVisibleForAllEntities(visible) {
        const { entity, updateEntity } = this.props
        let updatedList = _.cloneDeep(entity.entities)
        updatedList.forEach((row) => {
            row.active = visible
        })
        updateEntity({ 'entities': updatedList })
    }

    handleSchemaSelectAll = () => {
        const { entity } = this.props
        const schemas = _.uniqBy(entity.entities.map(e => ({ schema: e.schema })), 'schema')
        if (schemas.length > 0) {
            const model = this.schemaGrid.grid.current.api.getModel()
            const firstRow = model.rowsToDisplay[0]
            if (firstRow && firstRow.selected) {
                this.setVisibleForAllEntities(false)
                this.schemaGrid.grid.current.api.deselectAll()
            } else {
                this.setVisibleForAllEntities(true)
                this.schemaGrid.grid.current.api.selectAll()
            }
        }
    }

    handleObjectSelectAll = () => {
        const { entity, updateEntity } = this.props
        const rowAmount = this.objectGrid.grid.current.api.getDisplayedRowCount()
        if (rowAmount > 0) {
            const firstNode = this.objectGrid.grid.current.api.getRenderedNodes().length > 0 ?
                this.objectGrid.grid.current.api.getRenderedNodes()[0] : undefined
            if (firstNode) {
                let updatedList = _.cloneDeep(entity.entities)
                if (firstNode.selected) {
                    const selectedRows = this.objectGrid.grid.current.api.getSelectedRows()
                    updatedList.forEach((obj) => {
                        const selectedNodeIndex = selectedRows.findIndex(row => row.schema === obj.schema)
                        if(selectedNodeIndex >= 0){
                            obj.active = false
                        }
                    })
                    this.objectGrid.grid.current.api.deselectAll()
                } else {
                    const model = this.objectGrid.grid.current.api.getModel()
                    const rowsToDisplay = model.rowsToDisplay ? model.rowsToDisplay : []
                    rowsToDisplay.length > 0 && updatedList.forEach((obj) => {
                        const currentRowIndex = rowsToDisplay.findIndex(row => row.data.schema === obj.schema)
                        if(currentRowIndex >= 0){
                            obj.active = true
                        }
                    })
                    this.objectGrid.grid.current.api.selectAll()
                }
                updateEntity({ 'entities': updatedList })
            }
        }
    }

    getSchemaGridRowData = () => {
        const { entity } = this.props
        const uniqEntities = entity.entities ?
        _.uniqBy(entity.entities, 'schema')
        : []
        const rowData = uniqEntities.map(e => ({
            id: e.schema, 
            schema: e.schema, 
            tables: `${_.filter(entity.entities, {'schema': e.schema, 'active': true, 'importEntityType': "TABLE" }).length} / ${_.filter(entity.entities, {'schema': e.schema, 'importEntityType': "TABLE" }).length}`,
            views: `${_.filter(entity.entities, {'schema': e.schema, 'active': true, 'importEntityType': "VIEW" }).length} / ${_.filter(entity.entities, {'schema': e.schema, 'importEntityType': "VIEW" }).length}`
        }))
        return rowData
    } 

    onObjectGridReady = () => {
        this.updateObjectGridData()
    }

    updateObjectGridData() {
        const { entity, updateEntity } = this.props
        
        //Cause we have to sort selected (if they exist) only once
        if(!this.isSorted){
            const sorted = entity.entities.sort((a, b)=>b.active-a.active)
            updateEntity({ 'entities': sorted })
            /*We have to select the first schema (if there are no any selected shemas)
            to show a list of objects "just not to confuse users".*/
            this.selectFirstSchema()
            this.isSorted = true
        }

        const selectedSchemas = this.schemaGrid.grid.current.api.getSelectedRows()

        const filtered = _.filter(entity.entities, (element) => {
            return selectedSchemas.findIndex(s => s.schema === element.schema &&
                (element.importEntityType === 'TABLE' ? entity.showTables : entity.showViews)) >= 0
        })
        const rowData = filtered.map(d => ({
            name: d.name,
            schema: d.schema,
            _type_: d.importEntityType,
            deleted: d.deleted,
            transformation: d.templateTransformation ? d.templateTransformation.name : '',
            active: d.active
        }))

        this.objectGrid.grid.current.api.setRowData(rowData)
        this.selectActiveObjects()
    }

    componentDidUpdate(prevProps, prevState) {
        
        const { entity } = this.props

        if (entity.entities !== prevProps.entity.entities) {
            //updating schemaGrid
            const schemaGridRowData = this.getSchemaGridRowData()
            this.schemaGrid.grid.current.api.setRowData(schemaGridRowData)
            
            //updating objectGrid
            this.updateObjectGridData()
        }

        if (!_.isEqual(entity.entities, prevProps.entity.entities)) {
            const schemaGridRowData = this.getSchemaGridRowData()
            this.schemaGrid.grid.current.api.setRowData(schemaGridRowData)

            this.selectActiveObjects()
        }
        
        if (prevProps.entity.showTables !== entity.showTables || prevProps.entity.showViews !== entity.showViews) {
            this.updateObjectGridData()
        }
    }

    render() {
        const { t, entity } = this.props
        const getGridHeight = () => {
            if (entity.entities.length < 20) return '30vh'
            if (entity.entities.length < 50) return '50vh'
            return '70vh'
        }
        const suppressSpacekey = (params) => {
            if (params.event.keyCode === 32) {
                params.event.preventDefault()
            }
        }

        return (
            <Fragment>
                <br />
                <Row>
                    <Col span={6}>
                        <div style={{ height: getGridHeight(), overflow: 'auto' }}>
                            <div style={{ boxSizing: "border-box", height: "100%", width: "100%" }} className="ag-theme-balham">
                                <NfDataGrid
                                    headerSelection={true}
                                    onHeaderSelection={this.handleSchemaSelectAll}
                                    ref={(grid) => this.schemaGrid = grid}
                                    columnDefs={[
                                        { headerName: 'Schema', field: 'schema', sortingOrder: ["asc", "desc"], cellStyle: { 'font-size': '115%' }, suppressKeyboardEvent: suppressSpacekey },
                                        { headerName: 'Tables', width: 75, field: 'tables', sortingOrder: ["asc", "desc"], cellStyle: { 'font-size': '115%' }, suppressKeyboardEvent: suppressSpacekey },
                                        { headerName: 'Views', width: 75, field: 'views', sortingOrder: ["asc", "desc"], cellStyle: { 'font-size': '115%' }, suppressKeyboardEvent: suppressSpacekey }
                                    ]}
                                    gridOptions={{
                                        deltaRowDataMode: true,
                                        rowSelection: 'multiple',
                                        rowMultiSelectWithClick: true,
                                        enableCellChangeFlash: false,
                                        onSelectionChanged: (grid) => {
                                            const schemaGridRowData = this.getSchemaGridRowData()
                                            grid.api.setRowData(schemaGridRowData)
                                        },
                                        onGridReady: (grid) => {
                                            const schemaGridRowData = this.getSchemaGridRowData()
                                            grid.api.setRowData(schemaGridRowData)
                                            grid.api.forEachNode((node) => {
                                                const isSelected = entity.entities.findIndex(
                                                    o => o.active === true && o.schema === node.data.schema)
                                                isSelected >= 0 && node.setSelected(true)
                                            })
                                        },
                                        onCellClicked: this.handleSchemaGridClick,
                                        getRowNodeId: (data)=>data.id
                                    }}
                                />
                            </div>
                        </div>
                    </Col>
                    <Col span={13}>
                        <div style={{ height: getGridHeight(), overflow: 'auto' }}>
                            <div style={{ boxSizing: "border-box", height: "100%", width: "99%" }} className="ag-theme-balham">
                                <NfDataGrid
                                    headerSelection={true}
                                    onHeaderSelection={this.handleObjectSelectAll}
                                    ref={(grid) => this.objectGrid = grid}
                                    columnDefs={[
                                        {
                                            headerName: '', field: 'icon', suppressSorting: true, suppressFilter: true, width: 50, cellStyle: { 'font-size': '115%' },
                                            cellRenderer: function (params) {
                                                if (params.data._type_ === "TABLE") return `<svg width="20" height="20" xmlns="http://www.w3.org/2000/svg">
                                                 <g>
                                                  <rect x="-1" y="-1" width="22" height="22" id="canvas_background" fill="none"/>
                                                 </g>
                                                 <g>
                                                  <rect stroke="#3c9cfc" id="svg_2" height="15.37456" width="15.624552" y="2.304907" x="2.312724" stroke-width="1.5" fill="none"/>
                                                  <line stroke-linecap="null" stroke-linejoin="null" id="svg_4" y2="8.054742" x2="17.456027" y1="8.054742" x1="2.43772" stroke-width="1.5" stroke="#3c9cfc" fill="none"/>
                                                  <line stroke-linecap="null" stroke-linejoin="null" id="svg_5" y2="7.80475" x2="10.312494" y1="2.5549" x1="10.312494" stroke-width="1.5" stroke="#3c9cfc" fill="none"/>
                                                  <line stroke="#cccccc" stroke-linecap="null" stroke-linejoin="null" id="svg_6" y2="8.804721" x2="10.312494" y1="16.929488" x1="10.312494" fill-opacity="null" stroke-width="1.5" fill="none"/>
                                                  <line stroke="#cccccc" stroke-linecap="null" stroke-linejoin="null" id="svg_7" y2="12.804606" x2="17.187296" y1="12.804606" x1="3.062702" fill-opacity="null" stroke-opacity="null" stroke-width="1.5" fill="none"/>
                                                 </g>
                                                </svg>`
                                                return `<svg width="20" height="20" xmlns="http://www.w3.org/2000/svg">
                                                 <g>
                                                  <rect x="-1" y="-1" width="22" height="22" id="canvas_background" fill="none"/>
                                                 </g>
                                                 <g>
                                                  <rect stroke="#a1ce2f" id="svg_2" height="15.37456" width="15.624552" y="2.304907" x="2.312724" stroke-width="1.5" fill="none"/>
                                                  <rect stroke="#a1ce2f" id="svg_10" height="9.999714" width="10" y="5.054829" x="5.187641" stroke-width="1.5" fill="none"/>
                                                  <rect stroke="#a1ce2f" id="svg_11" height="3.999886" width="3.875176" y="8.054743" x="8.312551" stroke-width="1.5" fill="none"/>
                                                 </g>
                                                </svg>`
                                            },
                                            suppressKeyboardEvent: suppressSpacekey
                                        },
                                        { headerName: 'Object', field: 'name', width: 350, sortingOrder: ["asc", "desc"], cellStyle: (params) => ({ 'font-size': '115%', background: params.data.deleted ? '#ffa4a4' : undefined }), suppressKeyboardEvent: suppressSpacekey },
                                        { headerName: 'Schema', field: 'schema', width: 120, sortingOrder: ["asc", "desc"], cellStyle: { 'font-size': '115%' }, suppressKeyboardEvent: suppressSpacekey },
                                        { headerName: 'Transformation', field: 'transformation', width: 220, sortingOrder: ["asc", "desc"], cellStyle: { 'font-size': '115%' }, suppressKeyboardEvent: suppressSpacekey }
                                    ]}
                                    gridOptions={{
                                        deltaRowDataMode: true,
                                        rowSelection: 'multiple',
                                        rowMultiSelectWithClick: true,
                                        enableCellChangeFlash: true,
                                        onCellClicked: this.handleObjectGridClick,
                                        onGridReady: this.onObjectGridReady.bind(this),
                                        getRowNodeId: (data)=>data.name+data.schema
                                    }}
                                />
                            </div>
                        </div>
                    </Col>
                    <Col span={4}>
                        <div className="pretty-box" style={{ width: '100%', marginLeft: '30px', marginTop: '10px' }}>
                            <div className="pretty-box-title" style={{ fontWeight: 'bold' }}>
                                {t('rt.ImportWizard.views.viewsettings', { ns: ['classes'] })}
                            </div>
                            <Checkbox
                                style={{ marginBottom: '10px', marginTop: '10px' }}
                                checked={entity.showTables ? entity.showTables : false}
                                onChange={(e) => this.changeEntityValue("showTables", e.target.checked)}
                            >
                                {t('rt.ImportWizard.attrs.showTables.caption', { ns: ['classes'] })}
                            </Checkbox>
                            <br />
                            <Checkbox
                                style={{ marginBottom: '10px' }}
                                checked={entity.showViews ? entity.showViews : false}
                                onChange={(e) => this.changeEntityValue("showViews", e.target.checked)}
                            >
                                {t('rt.ImportWizard.attrs.showViews.caption', { ns: ['classes'] })}
                            </Checkbox>
                            <br />
                            {t('rt.ImportWizard.views.seltables', { ns: ['classes'] })}: &nbsp;
                                {`${_.filter(entity.entities, { 'active': true, 'importEntityType': "TABLE" }).length} /
                                ${_.filter(entity.entities, { 'importEntityType': "TABLE" }).length}`}
                            <br />
                            {t('rt.ImportWizard.views.selviews', { ns: ['classes'] })}: &nbsp;
                                {`${_.filter(entity.entities, { 'active': true, 'importEntityType': "VIEW" }).length} /
                                ${_.filter(entity.entities, { 'importEntityType': "VIEW" }).length}`}
                            
                        </div>
                    </Col>
                </Row>
            </Fragment>
        )
    }

}

class FinetuningStep extends Component {

    constructor(...args) {
        super(...args);
        this.state = {
            selectedObject: null,
            objectGridList: [],
            transformationList: []
        }
        this._isMounted = false
    }

    changeEntityValue = (selectedObject, key, value) => {
        value = value === "" ? null : value
        const { updateEntity, entity } = this.props
        const entityIndex = entity.entities.findIndex(e => e.name === selectedObject.name && e.schema === selectedObject.schema)
        const updatedEntities = update(entity.entities, {
            [entityIndex]: { [key]: { $set: value } }
        })
        updateEntity({ 'entities': updatedEntities })
    }

    getEntitySparkOptions = () => {
        const { entity } = this.props
        const { selectedObject } = this.state
        if (selectedObject) {
            const objectEntity = entity.entities.find(e => e.name === selectedObject.name && e.schema === selectedObject.schema)
            return objectEntity.sparkOpts
        }
        return []
    }

    getProjectTransformationList() {
        resource.query('/api/teneo/select/select e from etl.Transformation e').then(result => {
            this._isMounted && this.setState({ transformationList: result })
        })
    }

    componentWillUnmount() {
        this._isMounted = false
    }

    componentDidMount() {
        this._isMounted = true
        const objectGridList = _.filter(this.props.entity.entities, ['active', true])
        this.setState({ objectGridList })
        this.getProjectTransformationList()
    }

    componentDidUpdate(prevProps, prevState) {
        if (!_.isEqual(this.props.entity.entities, prevProps.entity.entities)) {
            const objectGridList = _.filter(this.props.entity.entities, ['active', true])
            this.setState({ objectGridList })
        }
    }

    render() {
        const { t, entity } = this.props
        const { selectedObject, objectGridList, transformationList } = this.state
        const { location } = window
        const getGridHeight = () => {
            if (entity.entities.length < 20) return '30vh'
            if (entity.entities.length < 50) return '50vh'
            return '70vh'
        }
        const sparkOptions = this.getEntitySparkOptions()
        const object = selectedObject ?
            entity.entities.find(e => e.name === selectedObject.name && e.schema === selectedObject.schema)
            : null

        return (
            <Fragment>
                <br />
                <Row>
                    <Col span={9}>
                        <div style={{ height: getGridHeight(), overflow: 'auto' }}>
                            <div style={{ boxSizing: "border-box", height: "100%", width: "100%" }} className="ag-theme-balham">
                                <NfDataGrid
                                    ref={(grid) => this.objectGrid = grid}
                                    columnDefs={[
                                        {
                                            headerName: '', field: 'icon', width: 50, sortingOrder: ["asc", "desc"], cellStyle: { 'font-size': '115%' },
                                            cellRenderer: function (params) {
                                                if (params.data.importEntityType === "TABLE") return `<svg width="20" height="20" xmlns="http://www.w3.org/2000/svg">
                                                 <g>
                                                  <rect x="-1" y="-1" width="22" height="22" id="canvas_background" fill="none"/>
                                                 </g>
                                                 <g>
                                                  <rect stroke="#3c9cfc" id="svg_2" height="15.37456" width="15.624552" y="2.304907" x="2.312724" stroke-width="1.5" fill="none"/>
                                                  <line stroke-linecap="null" stroke-linejoin="null" id="svg_4" y2="8.054742" x2="17.456027" y1="8.054742" x1="2.43772" stroke-width="1.5" stroke="#3c9cfc" fill="none"/>
                                                  <line stroke-linecap="null" stroke-linejoin="null" id="svg_5" y2="7.80475" x2="10.312494" y1="2.5549" x1="10.312494" stroke-width="1.5" stroke="#3c9cfc" fill="none"/>
                                                  <line stroke="#cccccc" stroke-linecap="null" stroke-linejoin="null" id="svg_6" y2="8.804721" x2="10.312494" y1="16.929488" x1="10.312494" fill-opacity="null" stroke-width="1.5" fill="none"/>
                                                  <line stroke="#cccccc" stroke-linecap="null" stroke-linejoin="null" id="svg_7" y2="12.804606" x2="17.187296" y1="12.804606" x1="3.062702" fill-opacity="null" stroke-opacity="null" stroke-width="1.5" fill="none"/>
                                                 </g>
                                                </svg>`
                                                return `<svg width="20" height="20" xmlns="http://www.w3.org/2000/svg">
                                                 <g>
                                                  <rect x="-1" y="-1" width="22" height="22" id="canvas_background" fill="none"/>
                                                 </g>
                                                 <g>
                                                  <rect stroke="#a1ce2f" id="svg_2" height="15.37456" width="15.624552" y="2.304907" x="2.312724" stroke-width="1.5" fill="none"/>
                                                  <rect stroke="#a1ce2f" id="svg_10" height="9.999714" width="10" y="5.054829" x="5.187641" stroke-width="1.5" fill="none"/>
                                                  <rect stroke="#a1ce2f" id="svg_11" height="3.999886" width="3.875176" y="8.054743" x="8.312551" stroke-width="1.5" fill="none"/>
                                                 </g>
                                                </svg>`
                                            }
                                        },
                                        { headerName: 'Object', field: 'name', sortingOrder: ["asc", "desc"], cellStyle: { 'font-size': '115%' } },
                                        { headerName: 'Schema', field: 'schema', width: 100, sortingOrder: ["asc", "desc"], cellStyle: { 'font-size': '115%' } },
                                        { headerName: 'Transformation', field: 'templateTransformation.name', width: 170, sortingOrder: ["asc", "desc"], cellStyle: { 'font-size': '115%' } }
                                    ]}
                                    rowData={objectGridList}
                                    gridOptions={{
                                        rowSelection: 'single',
                                        rowMultiSelectWithClick: false,
                                        onSelectionChanged: (grid) => {
                                            const rows = grid.api.getSelectedRows()
                                            this.setState({ selectedObject: rows.length > 0 ? rows[0] : null })
                                        },
                                        deltaRowDataMode: true,
                                        getRowNodeId: (data)=>data.name+data.schema
                                    }}
                                />
                            </div>
                        </div>
                    </Col>
                    <Col span={14}>
                        <div className="pretty-box" style={{ width: '100%', marginLeft: '20px' }}>
                            <div className="pretty-box-title" style={{ fontWeight: 'bold' }}>
                                {t('rt.ImportWizard.views.objectparams', { ns: ['classes'] })}
                            </div>
                            {object && <Fragment>
                                <div>{t('rt.ImportEntity.attrs.whereCondition.caption', { ns: ['classes'] })}</div>
                                <Input
                                    value={object && object.whereCondition ? object.whereCondition : undefined}
                                    onChange={(e) => this.changeEntityValue(object, "whereCondition", e.target.value)}
                                    style={{ width: '100%', marginBottom: '10px' }}
                                />
                                <Row>
                                    <Col span={4}>
                                        <div>{t('rt.ImportEntity.attrs.partitionField.caption', { ns: ['classes'] })}</div>
                                        <Input
                                            value={object && object.partitionField ? object.partitionField : undefined}
                                            onChange={(e) => this.changeEntityValue(object, "partitionField", e.target.value)}
                                            style={{ width: '100%' }}
                                        />
                                    </Col>
                                    <Col span={20} push={2}>
                                        <div>{t('rt.ImportEntity.attrs.preStatement.caption', { ns: ['classes'] })}</div>
                                        <Input
                                            value={object && object.preStatement ? object.preStatement : null}
                                            onChange={(e) => {
                                                this.changeEntityValue(object, "preStatement", e.target.value)
                                            }}
                                            style={{ width: '90%', marginBottom: '10px' }}
                                        />
                                    </Col>
                                </Row>
                                <div>{t('rt.ImportEntity.attrs.partitionExpression.caption', { ns: ['classes'] })}</div>
                                <Input
                                    value={object && object.partitionExpression ? object.partitionExpression : undefined}
                                    onChange={(e) => this.changeEntityValue(object, "partitionExpression", e.target.value)}
                                    style={{ width: '100%', marginBottom: '10px' }}
                                />
                                <div>{t('rt.ImportWizard.attrs.templateTransformation.caption', { ns: ['classes'] })}</div>
                                <Input.Group compact>
                                    <Select
                                        allowClear
                                        value={object && object.templateTransformation ? object.templateTransformation.name : []}
                                        style={{ width: '35%', marginBottom: '10px' }}
                                        onChange={(value) => {
                                            const transformationEntity = transformationList.find(o => o.name === value)
                                            this.changeEntityValue(object, "templateTransformation", { ...transformationEntity })
                                        }}
                                    >
                                        {transformationList.map(t =>
                                            <Select.Option key={t.name} value={t.name}>{t.name}</Select.Option>)}
                                    </Select>
                                    <Tooltip placement="top" title={t("edit")}>
                                        <Button type="dashed" placement="">
                                            {<a onClick={(e) => { e.stopPropagation() }}
                                                target="_blank"
                                                href={
                                                    object && object.templateTransformation && createHrefWithNewObject(location, {
                                                        e_id: object.templateTransformation.e_id,
                                                        _type_: object.templateTransformation._type_,
                                                        name: object.templateTransformation.name
                                                    })
                                                }>
                                                <Avatar className='avatar-button-property' size='small' src='images/icon-core/edit-modern.svg' />
                                            </a>}
                                        </Button>
                                    </Tooltip>
                                </Input.Group>
                                <Collapse
                                    bordered={false}
                                    expandIcon={({ isActive }) => <Icon type="caret-right" rotate={isActive ? 90 : 0} />}
                                >
                                    <Panel header={t('rt.ImportWizard.views.parallelismparams', { ns: ['classes'] })} key="1">
                                        <div>{t('rt.ImportEntity.attrs.idParallelism.caption', { ns: ['classes'] })}</div>
                                        <InputNumber
                                            value={object && object.idParallelism !== undefined ? object.idParallelism : undefined}
                                            onChange={(value) => this.changeEntityValue(object, "idParallelism", value)}
                                            style={{ width: '15%', marginBottom: '10px' }}
                                        />
                                        <div>{t('rt.ImportEntity.attrs.idField.caption', { ns: ['classes'] })}</div>
                                        <Input
                                            value={object && object.idField ? object.idField : undefined}
                                            onChange={(e) => this.changeEntityValue(object, "idField", e.target.value)}
                                            style={{ width: '30%', marginBottom: '10px' }}
                                        />
                                    </Panel>
                                    <Panel header={t('rt.ImportWizard.views.oozieparams', { ns: ['classes'] })} key="2">
                                        <Row>
                                            <Col span={4}>
                                                <div>{t('rt.ImportEntity.attrs.numExecutors.caption', { ns: ['classes'] })}</div>
                                                <InputNumber
                                                    value={object && object.numExecutors ? object.numExecutors : undefined}
                                                    onChange={(value) => this.changeEntityValue(object, "numExecutors", value)}
                                                    style={{ width: '90%', marginBottom: '10px' }}
                                                />
                                            </Col>
                                            <Col span={4}>
                                                <div>{t('rt.ImportEntity.attrs.executorCores.caption', { ns: ['classes'] })}</div>
                                                <InputNumber
                                                    value={object && object.executorCores ? object.executorCores : undefined}
                                                    onChange={(value) => this.changeEntityValue(object, "executorCores", value)}
                                                    style={{ width: '90%', marginBottom: '10px' }}
                                                />
                                            </Col>
                                        </Row>
                                        <Row>
                                            <Col span={4}>
                                                <div>{t('rt.ImportEntity.attrs.driverMemory.caption', { ns: ['classes'] })}</div>
                                                <Input
                                                    value={object && object.driverMemory ? object.driverMemory : undefined}
                                                    onChange={(e) => this.changeEntityValue(object, "driverMemory", e.target.value)}
                                                    style={{ width: '90%', marginBottom: '10px' }}
                                                />
                                            </Col>
                                            <Col span={4}>
                                                <div>{t('rt.ImportEntity.attrs.executorMemory.caption', { ns: ['classes'] })}</div>
                                                <Input
                                                    value={object && object.executorMemory ? object.executorMemory : undefined}
                                                    onChange={(e) => this.changeEntityValue(object, "executorMemory", e.target.value)}
                                                    style={{ width: '90%', marginBottom: '10px' }}
                                                />
                                            </Col>
                                        </Row>
                                    </Panel>
                                    <Panel header={t('rt.ImportEntity.attrs.sparkOpts.caption', { ns: ['classes'] })} key="3">
                                        <ParameterTable
                                            parameters={sparkOptions}
                                            onDataChange={this.changeEntityValue}
                                            object={object}
                                            objectType='rt.SparkOption'
                                            targetKey='sparkOpts'
                                        />
                                    </Panel>
                                </Collapse>
                            </Fragment>}
                        </div>
                    </Col>
                </Row>
            </Fragment>
        )
    }

}


const TContextStep = translate()(ContextStep);
const TObjectsStep = translate()(ObjectsStep);
const TFinetuningStep = translate()(FinetuningStep);
export { TContextStep as ContextStep, TObjectsStep as ObjectsStep, TFinetuningStep as FinetuningStep };
