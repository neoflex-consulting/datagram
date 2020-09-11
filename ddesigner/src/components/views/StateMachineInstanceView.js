import React, {Component, Fragment} from 'react';
import {translate} from "react-i18next";
import resource from "../../Resource";
import {Row, Col, Button, Badge, Tooltip} from 'antd';
import NfDataGrid from './../NfDataGrid';
import { createHrefWithNewObject } from './../../utils/encode'

class StateMachineInstanceView extends Component {

    constructor(...args) {
        super(...args);
        this.state = {stateList: [], availableTransitions: [], transitionHistory: []}
        this.shouldReadStates = true
    }

    getTransitionHistory(){
        resource.query("/api/teneo/select/select t.user,t.transitionDateTime,t.actionError,t.actionResult,t.succesfull,(t.transition.state.name||'->'||t.transition.toState.name) as transition from sm.SMInstance e join e.transitionInstances t where e.e_id=" + this.props.entity.e_id + "order by t.transitionDateTime desc").then(result => {
            if(result){
                this.setState({ transitionHistory: result.map(el => ({ user: el[0], transitionDateTime: el[1], actionError: el[2], actionResult: el[3], succesfull: el[4], transition: el[5] })) })
            }
        })
    }

    getStateList(){
        resource.query("/api/teneo/select/select e from sm.SMState e where smstate_statemachine_e_id=" + this.props.entity.stateMachine.e_id).then(result => {
            this.setState({ stateList: result })
        })
    }
    
    getAvailableTransitions() {
        const { activeObject } = this.props
        resource.callStatic("sm.SMInstance", "getAvailableTransitions", {_type_: activeObject._type_, e_id: activeObject.e_id}).then(response => {
            if(response.result === 'Finished!'){
                this.setState({availableTransitions: response.transitions})
            }
        })
    }

    goTo(transitionName) {
        const { activeObject } = this.props
        resource.callStatic("sm.SMInstance", "goTo", {_type_: activeObject._type_, e_id: activeObject.e_id, transition: transitionName}).then(response =>{
            if(response.result === 'Finished!'){
                this.getAvailableTransitions()
            }
        })
    }

    updateCurrentStatus(stateObject){
        this.props.updateEntity({ currentState: {e_id: stateObject.e_id, name: stateObject.name, _type_: stateObject._type_} })
    }

    componentDidMount() {
        this.getAvailableTransitions()
        this.getTransitionHistory()
    }

    componentDidUpdate(prevProps, prevState) {
        if(this.props.entity.stateMachine && this.shouldReadStates){
            this.getStateList()
            this.shouldReadStates = false
        }
        if(this.props.entity.currentState !== prevProps.entity.currentState){
            this.getAvailableTransitions()
        }
    }

    render() {
        const { entity, t } = this.props
        const { location } = window
        const { stateList, availableTransitions, transitionHistory } = this.state
        return (
            <div style={{ padding: '20px' }}>
            <div className="pretty-box" style={{ width: '100%', padding: '28px' }}>
            {entity.stateMachine && entity.currentState && <Fragment>
            <span style={{ fontSize: '32px', fontWeight: '500' }}>
                {`${entity.stateMachine.name}: ${entity.currentState ? entity.currentState.name : ""}`}
            </span>
            <div style={{ fontWeight: 'lighter', fontSize: '16px' }}>
                Instance of
                <a onClick={(e) => { e.stopPropagation() }}
                    target="_blank"
                    href={
                        createHrefWithNewObject(location, {
                            e_id: entity.stateMachine.e_id,
                            _type_: entity.stateMachine._type_,
                            name: entity.stateMachine.name
                        })}>
                    <span style={{ fontSize: '16px', fontWeight: 'lighter', fontVariantCaps: 'small-caps', cursor: 'pointer' }}>
                        {` ${entity.stateMachine.name} `}
                    </span>
                </a>
                in
                <a onClick={(e) => { e.stopPropagation() }}
                    target="_blank"
                    href={
                        createHrefWithNewObject(location, {
                            e_id: entity.target.e_id,
                            _type_: entity.target._type_,
                            name: entity.target.name
                        })}>
                    <span style={{ fontSize: '16px', fontWeight: 'lighter', fontVariantCaps: 'small-caps', cursor: 'pointer' }}>{` ${entity.target.name}`}</span>
                </a>
            </div>
            <Row style={{ marginTop: '15px' }}>
                <Col span={6}>
                {stateList.map(stateObject => {
                    return <Fragment key={stateObject.e_id}>
                        {entity.currentState.name === stateObject.name ? 
                            <span style={{ fontSize: '20px', fontWeight: '100' }}><Badge style={{marginLeft: '-15px'}} status="processing" />{stateObject.name}</span>
                        : 
                            <span style={{ fontSize: '20px', fontWeight: '100' }}>{stateObject.name}</span> }
                        <br />
                        {stateObject.transitions && 
                            stateObject.transitions.map(trans => (
                                <span key={trans.e_id} style={{fontSize: '15px', fontWeight: '100', marginLeft: '12px'}}>
                                    {availableTransitions.includes(trans.name) ? 
                                        <Fragment>
                                            <Badge status="success" />{trans.name}
                                        </Fragment> 
                                        :
                                        <Fragment> 
                                            <Badge status="default" />{trans.name}
                                        </Fragment>}
                                    {availableTransitions.includes(trans.name) && 
                                        <Tooltip placement="top" title={t("proceed")}>
                                            <Button style={{ marginLeft: '5px' }} 
                                            onClick={()=>{
                                                this.goTo(trans.name)
                                                this.updateCurrentStatus(trans.toState)
                                                this.getTransitionHistory()
                                            }} size="small" type="dashed" shape="circle" icon="arrow-right"/>
                                        </Tooltip>}
                                <br />
                                </span>
                            ))
                        }
                        <br />
                    </Fragment>
                })}
                </Col>
                <Col span={18}>
                    <div style={{ height: 'calc(100vh / 1.6)', overflow: 'auto' }}>
                            <div style={{ boxSizing: "border-box", height: "100%", width: "99%" }} className="ag-theme-balham">
                                <NfDataGrid
                                    ref={(grid) => this.objectGrid = grid}
                                    columnDefs={[
                                        { headerName: 'Date', field: 'transitionDateTime', width: 220, sortingOrder: ["asc", "desc"], cellStyle: { 'font-size': '115%' }, cellRenderer: function (params) {
                                            if (params.data.transitionDateTime) {
                                                return JSON.stringify(new Date(params.data.transitionDateTime))
                                            }
                                        } },
                                        { headerName: 'Transition', field: 'transition', width: 240, sortingOrder: ["asc", "desc"], cellStyle: { 'font-size': '115%' } },
                                        { headerName: 'User', field: 'user', width: 130, sortingOrder: ["asc", "desc"], cellStyle: { 'font-size': '115%' } },
                                        { headerName: 'Action error', field: 'actionError', width: 180, sortingOrder: ["asc", "desc"], cellStyle: { 'font-size': '115%' } },
                                        { headerName: 'Action result', field: 'actionResult', width: 180, sortingOrder: ["asc", "desc"], cellStyle: { 'font-size': '115%' } },
                                        { headerName: 'Successfull', field: 'successfull', width: 100, sortingOrder: ["asc", "desc"], cellStyle: { 'font-size': '115%' } }
                                    ]}
                                    rowData={transitionHistory}
                                    gridOptions={{
                                        rowSelection: 'single',
                                        rowMultiSelectWithClick: true,
                                        onSelectionChanged: (grid) => {
                                            //const rows = grid.api.getSelectedRows()
                                            //this.setState({ selectedObject: rows.length > 0 ? rows[0] : null })
                                        }
                                    }}
                                />
                            </div>
                        </div>
                </Col>
            </Row>
            </Fragment>}
            </div>
            </div>
        )
    }
}

export default translate()(StateMachineInstanceView);
