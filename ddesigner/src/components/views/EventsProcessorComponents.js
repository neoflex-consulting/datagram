import React, { Component } from 'react';
import { Button, Avatar, Dropdown, Menu, Input, Row, Col, Popover, Table, Icon, Modal } from 'antd';
import update from 'immutability-helper';
import PortsTree from '../PortsTree';
import reactStringReplace from 'react-string-replace';
import { getEntityClassFeature, getModel } from '../../model.js';
import { instantiate } from "../../utils/meta";
const { SubMenu } = Menu;

class EventsProcessorDataStructure {

    constructor(input, state, local, output) {
        this._input = input;
        this._output = output;
        this._state = state;
        this._local = local;
        this._newElementEid = 0;
    }

    get newElementEid() {
        this._newElementEid--;
        return this._newElementEid
    }

    get input() {
        return this._input
    }

    get output() {
        return this._output
    }

    get state() {
        return this._state
    }

    get local() {
        return this._local
    }

    set input(value) {
        this._input = value;
    }

    set state(value) {
        this._state = value;
    }

    set local(value) {
        this._local = value;
    }

    set output(value) {
        this._output = value;
    }

    getDataSetAlias = (dataSet) => {
        if(!dataSet) {
            return ""
        }
        if(this.input && (this.input === dataSet || (dataSet.e_id && this.input.e_id === dataSet.e_id))) {
         return 'input'
        }
        if(this.state && (this.state === dataSet || (dataSet.e_id && this.state.e_id === dataSet.e_id))) {
         return 'state'
        }
        if(this.local && (this.local === dataSet || (dataSet.e_id && this.local.e_id === dataSet.e_id))) {
         return 'local'
        }
        if(this.output && (this.output === dataSet || (dataSet.e_id && this.output.e_id === dataSet.e_id))) {
         return 'output'
        }
        return dataSet.alias ? dataSet.alias : ""
    };

    getDataSetByField = (field) => { // remove later
        if(!field) {
            return { fields : [] }
        }

        const dataSet = field.dataSet;

        if(this.input && (this.input.e_id === dataSet.e_id)) {
            return this.input
        }
        if(this.state && (this.state.e_id === dataSet.e_id)) {
            return this.state
        }
        if(this.local && (this.local.e_id === dataSet.e_id)) {
            return this.local
        }
        if(this.output && (this.output.e_id === dataSet.e_id)) {
            return this.output
        }

        return { fields : [] }
    };

    getProcessorFields = (types) => {
        var result = [];
        (types || []).forEach(datasetType => {
            switch(datasetType) {
                case("input"):
                    result.push(...(this.input || {fields: []}).fields)
                    break;
                case("state"):
                    result.push(...(this.state || {fields: []}).fields)
                    break;
                case("local"):
                    result.push(...(this.local || {fields: []}).fields)
                    break;
                case("output"):
                    result.push(...(this.output || {fields: []}).fields)
                    break;
                default:
            }

        });
        return result
    };

    getDataSetByAlias = (alias) => {
        let result = [];
        switch(alias) {
            case("input"):
                result = this.input;
                break;
            case("state"):
                result = this.state;
                break;
            case("local"):
                result = this.local;
                break;
            case("output"):
                result = this.output;
                break;
            default:
        }
        return result
    }

}

const RenderParam = ({elementId, param, value, datasets, fieldSelected, filterState, filterApplied, data}) => {
    const paramName = param.name;
    const alias = value ? data.getDataSetAlias(value.dataSet) : "";
    const valueName = value ? (alias === "" ? value.name : alias + "." + value.name) : "";
    return (
        <WrapSearchableComponent
            elementId={elementId}
            filterState={filterState}
            filterApplied={filterApplied}
            value={value ? valueName : paramName}
        >
            <ParamPopover
                datasets={datasets}
                OnFieldSelected={(event) => {
                    fieldSelected(event)
                }}
                selectedField={value}
                data={data}
                render={()=><b><a className="ant-dropdown-link" href={null}>
                                {value ? valueName : paramName}
                </a></b>}
            />
        </WrapSearchableComponent>
    )
};

const SelectFunctionDialog = ({functions, selected, onAppySelected, filterStr}) => {
    function showSearch(text) {
        return (filterStr && filterStr !== "") ? (text ?
            reactStringReplace(text, filterStr, (match, i) => (
                <span key={i} style={{ color: 'red' }}>{match}</span>
            )) : text) : text
    }
    const columns = [
        {title: "", dataIndex: "", key: "btn", render: (text, record, index) => {return <Button onClick={()=>{onAppySelected(record)}}>Apply</Button>}},
        {title: "name", dataIndex: "name", key: "name", render: (text) => showSearch(text)},
        {title: "userTextPattern", dataIndex: "userTextPattern", key: "userTextPattern", render: (text) => showSearch(text)},
        {title: "description", dataIndex: "description", key: "description", render: (text) => showSearch(text)},
        {title: "library", dataIndex: "library.name", key: "library", render: (text) => showSearch(text)}];
    return <div style={{height: '400px', overflow: 'auto'}}>
        <Table
            size={"small"}
            pagination={false}
            columns={columns}
            rowKey={(record)=>record.e_id}
            dataSource={functions.filter(f=>{return (!filterStr || filterStr === "") ||
                        (
                            (filterStr && filterStr !== "") && ((f.name || "").includes(filterStr) ||
                            (f.userTextPattern || "").includes(filterStr) ||
                            (f.description || "").includes(filterStr) ||
                            (f.library.name).includes(filterStr))
                        )
                    }
                )}
        />
    </div>
};

class ActionPopover extends Component {
    constructor(...args) {
        super(...args);
        this.state = {filterStr: ""}
    }

    closePopover() {
        this.setState({visible: false})
    }

    renderContent() {
        return (
            <React.Fragment>
                <Input onChange={(e)=>this.setState({filterStr: e.target.value})} value={this.state.filterStr}/>
                <SelectFunctionDialog
                    filterStr={this.state.filterStr}
                    {...this.props}
                    onAppySelected={(val)=>{
                        this.closePopover();
                        this.props.onAppySelected(val);
                    }}/>
            </React.Fragment>)
    }

    render() {
        return <Popover trigger="click" content={this.renderContent()}
            visible={this.state.visible}
            onVisibleChange={(visible)=>this.setState({visible})}>
            {this.props.render()}
        </Popover>
    }
}

class ParamPopover extends ActionPopover {
    renderContent() {
        return <SelectFieldDialog
                {...this.props}
                OnfieldSelected={(val)=>{
                    this.closePopover();
                    this.props.OnFieldSelected(val);
            }}
            />
    }
}

const RenderSetValueAction = ({elementId, action, data, filterState, filterApplied, updateEntity}) => {
    let list = data.getProcessorFields(["local", "state", "output"]);
    return <Row>
        <Col span={2}>
            <div className={"ruleLabel r2"}>
                Set
            </div>
        </Col>
        <Col span={6}>
        <RenderParam
            elementId={elementId + "_output"}
            param={{name: "output..."}}
            value={action.outputField ? list[getIndex(list, action.outputField)] : undefined}
            datasets={[data.output, data.state, data.local]}
            fieldSelected={(e)=>{
                let field = e.node.props.field;
                updateEntity(action, update(action, {$merge: {outputField: field}}))
            }}
            filterState={filterState}
            filterApplied={filterApplied}
            data={data}
        />
        </Col>
        <Col span={2}>
            <div className={"ruleLabel r3"}>
                as
            </div>
        </Col>
        <Col span={14}>
            <WrapSearchableComponent
                elementId={elementId + "_expression"}
                filterState={filterState}
                filterApplied={filterApplied}
                value={action.expression}
            >
                <Input.TextArea value={action.expression} rows={1} style={{width: '300px', border: 'none'}}
                      onChange={(e)=>{
                          if(updateEntity) {
                              updateEntity(action, update(action, {$merge: {expression: e.target.value}}))
                          }
                      }
                  }/>
            </WrapSearchableComponent>
         </Col>
      </Row>
};

const RenderSetTimeoutAction = ({elementId, action, updateEntity, filterState, filterApplied}) => {
    return <Row>
        <Col span={2}>
            <div className={"ruleLabel r4"}>
                Set group timeout
            </div>
        </Col>
        <Col span={22}>
            <WrapSearchableComponent
                elementId={elementId}
                filterState={filterState}
                filterApplied={filterApplied}
                value={action.duration}
            >
                <Input value={action.duration} rows={1}
                      onChange={(e)=>{
                          if(updateEntity) {
                              updateEntity(action, update(action, {$merge: {duration: e.target.value}}))
                          }
                      }
                  }/>
            </WrapSearchableComponent>
          </Col>
      </Row>
};

const RenderMapValueAction = ({elementId, action, data, updateEntity, filterState, filterApplied}) => {
    let listValue = data.getProcessorFields(["local", "state", "output"])
    let listOutput = data.getProcessorFields(["input", "local", "state", "output"])
    return <Row>
            <Col span={2}>
                Set
            </Col>
            <Col span={6}>
                <RenderParam
                    elementId={elementId + "_output"}
                    param={{name: "output..."}}
                    value={action.outputField ? listValue[getIndex(listValue, action.outputField)] : undefined}
                    datasets={[data.output, data.state, data.local]}
                    fieldSelected={(e)=>{
                        let field = e.node.props.field
                        updateEntity(action, update(action, {$merge: {outputField: field}}))
                    }}
                    data={data}
                    filterState={filterState}
                    filterApplied={filterApplied}
                />
        </Col>
        <Col span={2}>
            as
        </Col>
        <Col span={8}>
            <RenderParam
                elementId={elementId + "_value"}
                param={{name: "value..."}}
                value={action.valueField ? listOutput[getIndex(listOutput, action.valueField)] : undefined}
                datasets={[data.input, data.state, data.local, data.output]}
                fieldSelected={(e)=>{
                    let field = e.node.props.field
                    updateEntity(action, update(action, {$merge: {valueField: field}}))
                }}
                filterState={filterState}
                filterApplied={filterApplied}
                data={data}/>
        </Col>
      </Row>
};

const RenderFunctionAction = ({elementId, action, functions, data, filterState, filterApplied, updateEntity}) => {
    let functionPattern = action.name || "No function's selected";
    var f = undefined;
    if(action.function) {
        f = (functions || []).find(fs=>fs.e_id === action.function.e_id);
        if(f) {
            functionPattern = f.userTextPattern || f.name
        }
    }
    let wordsArray = functionPattern.split(/([., ()[\]])/).map(s=>{return {original: s, isreplaced: false, newvalue: s}});
    var outputs = [];
    if(f){
        (f.input || {fields: []}).fields.forEach((input, index)=>{
            let mapping = (action.paramsMapping || []).find(pm=>pm.param.name === input.name);
            let s = <RenderParam
                elementId={elementId + "_param_" + index}
                key={"param_" + index}
                param={input}
                value={mapping ? mapping.value: undefined}
                datasets={[data.input, data.state, data.local, data.output]}
                filterState={filterState}
                filterApplied={filterApplied}
                fieldSelected={(e)=>{
                 if(mapping) {
                     let idx = action.paramsMapping.indexOf(mapping);
                     const newMapping = update(mapping, {$merge: {value: e.node.props.field}})
                     updateEntity(action, update(action, {paramsMapping: {[idx]: {$merge: newMapping}}}))
                 } else {
                     let newp = {
                         _type_: "evs.FunctionParamsMapping",
                         param: input,
                         value: e.node.props.field
                     };
                     const newParamsMapping = update(action.paramsMapping || [], {$push: [newp]});
                     updateEntity(action, update(action, {$merge: {paramsMapping: newParamsMapping}}))
                 }
             }}
             data={data}/>;
            let pos = wordsArray.find(s=>s.original === '{' + input.name + '}');
            if(pos) {
                pos.newvalue = s;
                pos.isreplaced = true
            }
        });

        (f.output || {fields: []}).fields.forEach((output, index) => {
            var mapping = (action.resultsMapping || []).find(rm=>rm.param.name === output.name);
            let s = <RenderParam
                elementId={elementId + "_output_" + index}
                key={"output_" + index}
                param={output}
                value={mapping ? mapping.value: undefined}
                datasets={[data.state, data.local, data.output]}
                filterState={filterState}
                filterApplied={filterApplied}
                fieldSelected={(e)=>{
                 if(mapping) {
                     let idx = action.resultsMapping.indexOf(mapping);
                     const newMapping = update(mapping, {$merge: {value: e.node.props.field}});
                     updateEntity(action, update(action, {resultsMapping: {[idx]: {$merge: newMapping}}}))
                 } else {
                     let newp = {
                         _type_: "evs.FunctionOutputMapping",
                         param: output,
                         value: e.node.props.field
                     };
                     const newResultsMapping = update(action.resultsMapping || [], {$push: [newp]});
                     updateEntity(action, update(action, {$merge: {resultsMapping: newResultsMapping}}))
                 }
             }}
             data={data}/>;

            outputs.push(s)
        })
    }

    wordsArray = wordsArray.map((w, index)=>{return w.isreplaced ? w.newvalue :<ActionPopover
        key={index}
        action={action}
        functions={functions}
        updateEntity={updateEntity}
        onAppySelected={(func)=>{updateEntity(action, update(action, {$merge: {[`function`]: func, paramsMapping: [], resultsMapping: []}}));}} //`
        render={()=><a href={null}>{w.original}</a>}/>});

    return(
        <WrapSearchableComponent
            elementId={elementId}
            filterState={filterState}
            filterApplied={filterApplied}
            value={wordsArray + "set to" + outputs}
        >
            <p>{wordsArray} set to {outputs}</p>
        </WrapSearchableComponent>
    )
};

function getIndex(list, value) {
    if(!value) {
        return value
    }
    var idx = list.indexOf(value);
    if(idx > -1) {
        return idx;
    }
    list.forEach((item, index)=>{
        if(item.e_id && item.e_id === value.e_id) {
            idx = index
        }
    });
    if(idx === -1) {
        list.forEach((item, index)=>{
            if(item.name && item.name === value.name) {
                idx = index
            }
        })
    }
    return idx === -1 ? undefined : idx
}

const RenderParamsCondition = ({elementId, condition, rule, updateEntity, filterState, filterApplied, data}) => {
    let listLeft = data.getProcessorFields(["input", "local", "state", "output"]);
    let setLeftParamValue = (e) => {
        let f = e.node.props.field;
        updateEntity(update(condition, {$merge: {leftParam: f}}))
    };
    let setRightParamValue = (e) => {
        let f = e.node.props.field;
        updateEntity(update(condition, {$merge: {rightParam: f}}))
    };
    let getOperandStr=(key)=>{
        switch(key){
            case "equal":
                return '=';
            case "notEqual":
                return '<>';
            case "more":
                return '>';
            case "moreOrEqals":
                return '>=';
            case "less":
                return '<';
            case "lessOrEqals":
                return '<=';
            default:
                return '...'
        }
    };
    const operandMenu=(
        <Menu onClick={(e)=>updateEntity(update(condition, {$merge: {operand: e.key}}))}>
            <Menu.Item key={"equal"}>=</Menu.Item>
            <Menu.Item key={"notEqual"}>&lt;&gt;</Menu.Item>
            <Menu.Item key={"more"}>&gt;</Menu.Item>
            <Menu.Item key={"moreOrEqals"}>&gt;=</Menu.Item>
            <Menu.Item key={"less"}>&lt;</Menu.Item>
            <Menu.Item key={"lessOrEqals"}>&lt;=</Menu.Item>
        </Menu>);
    return (<Row>
            <Col span={24}>
                <div className={"ruleLabel r9"}>
                    <RenderParam
                        elementId={elementId + "_left"}
                        param={{name: "left..."}}
                        value={condition.leftParam ? listLeft[getIndex(listLeft, condition.leftParam)] : undefined}
                        datasets={[data.input, data.output, data.state, data.local]}
                        filterState={filterState}
                        fieldSelected={(e)=>setLeftParamValue(e)} data={data}/>
                    &nbsp;
                    <Dropdown overlay={operandMenu}>
                            <b><a href={null}>{getOperandStr(condition.operand)}</a></b>
                    </Dropdown>
                    &nbsp;
                </div>
            {condition._type_ === "evs.ParamsCompareCondition" &&
            <div className={"ruleLabel r10"}>
                <RenderParam
                    elementId={elementId + "_right"}
                    param={{name: "right..."}}
                    value={condition.rightParam ? listLeft[getIndex(listLeft, condition.rightParam)] : undefined}
                    datasets={[data.input, data.output, data.state, data.local]}
                    fieldSelected={(e)=>setRightParamValue(e)}
                    filterState={filterState}
                    filterApplied={filterApplied}
                    data={data}/>
            </div>
            }
            {condition._type_ === "evs.ParamsToExpressionCondition" &&

                <WrapSearchableComponent
                    elementId={elementId}
                    filterState={filterState}
                    filterApplied={filterApplied}
                    value={condition.expression}
                >
                    <Input.TextArea value={condition.expression} rows={1} style={{width: '300px', border: 'none'}}
                          onChange={(e)=>{
                              if(updateEntity) {
                                  updateEntity(update(condition, {$merge: {expression: e.target.value}}))
                              }
                          }
                      }/>
                </WrapSearchableComponent>
            }
            </Col>
        </Row>)
};

const RenderActionPanel = ({rule, OnNewAction}) => {
    const menu = (
      <Menu onClick={(e)=>{
          let newAction = {_type_: e.key}

          OnNewAction(rule, newAction)
      }}>
        <Menu.Item key="evs.FunctionAction">Function</Menu.Item>
        <Menu.Item key="evs.SetValueAction">Set value</Menu.Item>
        <Menu.Item key="evs.MapValueAction">Map value</Menu.Item>
        <Menu.Item key="evs.SetTimeoutAction">Timeout</Menu.Item>
        <Menu.Item key="evs.AddOutputAction">Add output</Menu.Item>
        <Menu.Item key="evs.RemoveStateAction">Remove state</Menu.Item>
        <Menu.Item key="evs.InitializeFactAction">Initialize fact</Menu.Item>
      </Menu>
    );

    return <div className={"ruleAction"}>
        <Dropdown overlay={menu}>
            <Button>
                <Avatar src='images/icon-core/file-add.svg' />
            </Button>
        </Dropdown>
    </div>
};

const RenderLogicConditionPanel = ({condition, rule, newCondition}) => {
    const menu = (
      <Menu onClick={(e)=>{
          let newCond = {_type_: e.key}

          if(e.key === 'evs.AndCondition' || e.key === 'evs.OrCondition') {
              newCond.conditions = []
          }
          newCondition(rule, condition, newCond)
      }}>
        <Menu.Item key="evs.AndCondition">AND</Menu.Item>
        <Menu.Item key="evs.OrCondition">OR</Menu.Item>
        <Menu.Item key="evs.EvalCondition">Expression</Menu.Item>
        <Menu.Item key="evs.ParamsCompareCondition">Params</Menu.Item>
        <Menu.Item key="evs.ParamsToExpressionCondition">Params - Expression</Menu.Item>
      </Menu>
    );

    return <div className={"ruleAction"}>
        <Dropdown overlay={menu}>
            <Button>
                <Avatar className='avatar-add' src='images/icon-core/file-add.svg' />
            </Button>
        </Dropdown>
    </div>
};

function renderLogicCondition(elementId, condition, rule, updateEntity, data, filterState, filterApplied, t) {
    var newCondition=(rule, parentCondition, conditionToAdd)=>{
        const updatedConditions = update(parentCondition.conditions || [], {$push: [conditionToAdd]})
        updateEntity(update(parentCondition, {$merge: {conditions: updatedConditions}}))
    };
    return <React.Fragment>
            <Row>
                <Col span={21}>{condition._type_ === "evs.AndCondition" ? t("allTrue", { ns: 'common' }) : t("orCondition", { ns: 'common' })}</Col>
                <Col span={3}><RenderLogicConditionPanel condition={condition} rule={rule} t={t} newCondition={(rule, condition, newCond) => newCondition(rule, condition, newCond)} /></Col>
            </Row>
            <Row><Col span={24}>{(condition.conditions || []).map((record, index) => {
                    return <RenderCondition
                        elementId={elementId + "_condition" + index}
                        key={"condition" + index}
                        condition={record}
                        index={index}
                        rule={rule}
                        parentCondition={condition}
                        filterState={filterState}
                        filterApplied={filterApplied}
                        updateEntity= {(changes)=>
                        {
                            const updated = update(condition, {conditions: {[index]: {$merge: changes}}})
                            if(updateEntity) {
                                updateEntity(updated)
                            }
                        }}
                        deleteItem = {()=>{

                            Modal.confirm({
                                content: t("confirmdelete"),
                                okText: t("delete"),
                                cancelText: t("cancel"),
                                onOk: () => {
                                    const updated = update(condition, {$merge:{
                                            conditions: update(condition.conditions, {$splice: [[index, 1]]})
                                        }});
                                    updateEntity(updated)
                                }
                            });
                        }}
                        upItem = {()=>{
                            if (index > 0) {
                                let list = condition.conditions.slice();
                                const updated = update(condition, {$merge:
                                    {conditions: [
                                        ...list.slice(0, index - 1),
                                        list[index],
                                        list[index - 1],
                                        ...list.slice(index + 1)
                                    ]}
                                });
                                updateEntity(updated);
                            }
                        }}
                        downItem = {()=>{
                            let list = condition.conditions.slice();
                            if (index >= 0 && index < list.length - 1) {
                                const updated = update(condition, {$merge:
                                    {conditions: [
                                        ...list.slice(0, index),
                                        list[index + 1],
                                        list[index],
                                        ...list.slice(index + 2)
                                    ]}
                                });
                                updateEntity(updated);
                            }
                        }}
                        newCondition={(rule, condition, newCond) => newCondition(rule, condition, newCond)}
                        data={data} t={t}/>
                })}
                </Col>
            </Row>
        </React.Fragment>
}

const RenderCondition = ({elementId, condition, rule, parentCondition, updateEntity, deleteItem, upItem, downItem, data,
                             filterState, filterApplied, t}) => {
    let comp = <Row type="flex" justify="end">
                {rule.condition !== condition &&
                    <Col/>
                }
               <Col span={rule.condition === condition ? 24 : 20}>
                   {condition._type_ === "evs.EvalCondition" &&

                   <WrapSearchableComponent
                       elementId={elementId}
                       filterState={filterState}
                       filterApplied={filterApplied}
                       value={condition.expression}
                   >
                     <Input.TextArea value={condition.expression} rows={1} style={{width: '600px', border: 'none'}}
                           onChange={(e)=>{
                               if(updateEntity) {
                                   updateEntity(update(condition, {$merge: {expression: e.target.value}}))
                               }
                           }
                       }/>
                   </WrapSearchableComponent>
                   }
                   {(condition._type_  === "evs.AndCondition" || condition._type_ === "evs.OrCondition") &&
                       renderLogicCondition(elementId, condition, rule, updateEntity, data, filterState, filterApplied, t)
                   }
                   {condition._type_  === "evs.ParamsCompareCondition" &&
                       <RenderParamsCondition condition={condition} rule={rule} updateEntity={updateEntity} data={data}
                                              filterState={filterState} filterApplied={filterApplied}
                                              elementId={elementId} t={t} />
                   }
                   {condition._type_  === "evs.ParamsToExpressionCondition" &&
                       <RenderParamsCondition condition={condition} rule={rule} updateEntity={updateEntity} data={data}
                                              filterState={filterState} filterApplied={filterApplied}
                                              elementId={elementId} t={t} />
                   }
               </Col>
               {rule.condition !== condition &&
               <Col span={3}>
                    <RowBtnGroup deleteRow={()=>deleteItem()} upRow={()=>upItem()} downRow={()=>downItem()} t={t} />
               </Col>}
           </Row>
    return comp;
};

const RenderAction = ({key, action, functions, data, updateEntity, deleteAction, upAction, downAction, filterState,
                          filterApplied, elementId, t}) => {
    var actionX;
     if(action._type_ === "evs.FunctionAction") {
         actionX = (
             <RenderFunctionAction
                elementId={elementId}
                action={action}
                functions={functions}
                data={data}
                updateEntity={updateEntity}
                filterState={filterState}
                filterApplied={filterApplied}
                t={t}/>)
     } else if(action._type_ === "evs.SetValueAction") {
         actionX = (
             <RenderSetValueAction
                 elementId={elementId}
                action={action}
                data={data}
                filterState={filterState}
                updateEntity={updateEntity}
                filterApplied={filterApplied}
                t={t}/>)
     } else if(action._type_ === "evs.MapValueAction") {
         actionX = (
             <RenderMapValueAction
                 elementId={elementId}
                action={action}
                data={data}
                updateEntity={updateEntity}
                filterState={filterState}
                filterApplied={filterApplied}
                t={t}/>)
     } else if(action._type_ === "evs.SetTimeoutAction") {
         actionX = (
             <RenderSetTimeoutAction
                 elementId={elementId}
                action={action}
                updateEntity={updateEntity}
                filterState={filterState}
                filterApplied={filterApplied}
                t={t}/>)
     } else {
         actionX = (
             <div>
                {action._type_}
             </div>
         )
    }
    return <Row type="flex" justify="end">
               <Col span={20}>
                   <WrapSearchableComponent
                       elementId={elementId}
                       filterState={filterState}
                       filterApplied={filterApplied}
                       value={action._type_}
                   >
                    {actionX}
                   </WrapSearchableComponent>
               </Col>
               <Col span={3}>
                    <RowBtnGroup deleteRow={()=>deleteAction()} upRow={()=>upAction()} downRow={()=>downAction()} t={t} />
               </Col>
           </Row>;
};

const RowBtnGroup = ({deleteRow, upRow, downRow, t}) => {

    const menu = (
      <Menu onClick={(e)=>{
          if(e.key === "up") {
              upRow()
          }
          if(e.key === "down") {
              downRow()
          }
          if(e.key === "del") {
              deleteRow()
          }
      }}>
        <Menu.Item key="up"><Avatar className="button-avatar" size="small" src='images/icon-core/arrow-up-modern.svg' /></Menu.Item>
        <Menu.Item key="down"><Avatar className="button-avatar" size="small" src='images/icon-core/arrow-down-modern.svg' /></Menu.Item>
        <Menu.Item key="del"><Avatar className="button-avatar" size="small" src='images/icon-core/delete-modern.svg' /></Menu.Item>
      </Menu>
    );

    return <div className={"ruleAction"}>
        <Dropdown overlay={menu}>
            <Button>
                <Avatar size="small" src='images/icon-core/etl.svg' />
            </Button>
        </Dropdown>
    </div>
};

const SelectFieldDialog = ({datasets, OnfieldSelected, selectedField, data, defaultAlias}) => {
    return (<div style={{width: '400px', height: '400px', overflow: 'auto'}}>
        <PortsTree ports={datasets}
                getAlias={(dataSet) => data.getDataSetAlias(dataSet)}
                treeOnClick={(event) => {
                    if(OnfieldSelected) {
                        OnfieldSelected(event);
                    }
                }}
                selectedField={selectedField}
                defaultAlias={defaultAlias}
            />
        </div>)
};

const GetNewFieldsMenu = (data, path, translate, addAction, isEmbedded, isOnlyField) => {

    let field = null;
    let successors = null;

    let parent = GetTreeFieldByPath(data, path);

    if (isOnlyField) {
        successors = ["dataset.Field"];

    } else {
        if (parent.fields) {
            const fields = getEntityClassFeature(getModel(), null, parent, "fields");
            field = fields[0];
            const context = {__parent: parent};
            successors = getEntityClassFeature(getModel(), field.entityType, context, "successors");
        }

        if (parent.domainStructure) {
            const fields = getEntityClassFeature(getModel(), null, parent.domainStructure.internalStructure, "fields");
            field = fields[0];
            const context = {__parent: parent};
            successors = getEntityClassFeature(getModel(), field.entityType, context, "successors");
        }
    }

    if (!isEmbedded) {
        return <Menu onClick={e => {
            e.domEvent.stopPropagation();
            addAction(parent, path, e.key);
        }}>
            {successors.map(embeddedType =>
                <Menu.Item
                    key={embeddedType}>{translate(`${embeddedType}.caption`, {ns: 'classes'})}</Menu.Item>)}
        </Menu> //`

    } else {
        let title = <Avatar className='avatar-add' src='images/icon-core/file-add-simple.svg'/>;

        return <SubMenu title={title} onClick={e => {
            e.domEvent.stopPropagation();
            addAction(parent, path, e.key);
        }}>
            {successors.map(embeddedType =>
                <Menu.Item
                    key={embeddedType}>{translate(`${embeddedType}.caption`, {ns: 'classes'})}</Menu.Item>)}
        </SubMenu> //`
    }
};

const RenderGroupAction = ({data, path, translate, addAction}) => {

    const createMenu = GetNewFieldsMenu(data, path, translate, addAction, false, true);
    return (<div className={"treeAction"}><Dropdown overlay={createMenu} className="treeAction">
                <Button size="small" placement="" >
                    <Avatar className='avatar-add' src='images/icon-core/file-add.svg'/>
                </Button>
    </Dropdown></div>)
};

const RenderTreeAction = ({data, path, alias, editAction, upAction, downAction, deleteAction, addAction, translate}) => {

    let field = GetTreeFieldByPath(data, path);

    const menu = (
        <Menu onClick={(e)=>{

            e.domEvent.stopPropagation();

            switch(e.key) {
                case("add"):
                    addAction(path);
                    break;
                case("edit"):
                    editAction(path);
                    break;
                case("up"):
                    upAction(path);
                    break;
                case("down"):
                    downAction(path);
                    break;
                case("delete"):
                    deleteAction(path);
                    break;
                default:
            }
            }} mode="vertical">

            {addAction && (!!field.fields || !!field.domainStructure) &&
                GetNewFieldsMenu(data, path, translate, addAction, true, false)
            }

            {editAction &&
                <Menu.Item key="edit"><Icon type="edit" className="alignedIcon"/></Menu.Item>
            }

            {upAction &&
                <Menu.Item key="up"><Avatar className="button-avatar" size="small"
                                        src='images/icon-core/arrow-up-modern.svg'/></Menu.Item>
            }

            {downAction &&
                <Menu.Item key="down"><Avatar className="button-avatar" size="small"
                                        src='images/icon-core/arrow-down-modern.svg'/></Menu.Item>
            }

            {deleteAction &&
                <Menu.Item key="delete"><Avatar className="button-avatar" size="small"
                                         src='images/icon-core/delete-modern.svg'/></Menu.Item>
            }
        </Menu>
    );

    return <Dropdown overlay={menu} className="treeAction">
                <Button>
                    <Avatar src='images/icon-core/etl.svg' />
                </Button>
           </Dropdown>
};


const GetParentPath = (path) => {

    let pathElements = path.split("^");
    pathElements = pathElements.slice(0, pathElements.length-1);

    let parentPath = pathElements.join("^");
    return parentPath;
};

const GetLastElement = (path) => {

    let pathElements = path.split("^");
    pathElements = pathElements.slice(pathElements.length-1);

    return pathElements[0];
};

const DeleteRulesElement = (updateEntity, list, text) => {

    let idx = -1;

    for(let i=0; i<list.length; i++) {
        if (list[i].name === text) {
            idx = i;
            break;
        }
    }

    if (idx === -1) {
        return;
    }

    updateEntity({
        rules: update(list, {$splice: [[idx, 1]]})
    });
};

const MoveRulesElement = (updateEntity, list, text, isUp) => {

    let idx = -1;

    for(let i=0; i<list.length; i++) {
        if (list[i].name === text) {
            idx = i;
            break;
        }
    }

    if (idx === -1 ||
        (isUp && idx === 0) ||
        (!isUp && idx === list.length-1)) {
        return;
    }

    const newList = [
        ...list.slice(0, idx + (isUp ? -1 : 0)),
        list[idx + (isUp ?  0 : 1)],
        list[idx + (isUp ? -1 : 0)],
        ...list.slice(   idx + (isUp ?  1 : 2))
    ];

    updateEntity({
        rules: newList
    });
};

const MoveTreeElement = (data, path, updateEntity, isUp) => {

    let parentPath = GetParentPath(path);
    let itemName = GetLastElement(path);

    DoActionWithDataSetsField(data, parentPath, updateEntity, (parent, current, field) => {

        let list = null;

        if(current.fields) {
            list = current.fields;
        }

        if (current.domainStructure) {
            list = current.domainStructure.internalStructure.fields;
        }

        let idx = -1;

        for(let i=0; i<list.length; i++) {
            if (list[i].name + i === itemName) {
                idx = i;
                break;
            }
        }

        if (idx === -1) {
            return current;
        }

        if (isUp && (idx < 1 || idx >= list.length)) {
            return current;
        }

        if (!isUp && (idx < 0 || idx >= list.length - 1)) {
            return current;
        }

        const newList = [
            ...list.slice(0, idx + (isUp ? -1 : 0)),
            list[idx + (isUp ?  0 : 1)],
            list[idx + (isUp ? -1 : 0)],
            ...list.slice(   idx + (isUp ?  1 : 2))
        ];

        if(current.fields) {
            current.fields = newList;
        }

        if (current.domainStructure) {
            current.domainStructure.internalStructure.fields = newList;
        }

        return current;
    });
};

const DeleteTreeElement = (t, data, field, updateEntity) => {

    Modal.confirm({
        content: t("confirmdelete"),
        okText: t("delete"),
        cancelText: t("cancel"),
        onOk: () => {
            DoActionWithDataSetsField(data, field, updateEntity, (parent, current, field) => {
                return undefined;
            });
        }
    });
};

const AddTreeElement = (data, path, key, updateEntity) => {

    let newField = instantiate(key);

    DoActionWithDataSetsField(data, path, updateEntity, (parent, current, elementPath) => {

        if (current.fields) {
            current.fields = update(current.fields, {$push: [newField]});
        }

        if (current.domainStructure) {
            current.domainStructure.internalStructure.fields =
                update(current.domainStructure.internalStructure.fields, {$push: [newField]});
        }

        return current;
    });
};

const DoActionWithFieldChilds = (parent, childs, searchPath, action, path) => {

    for(let i=0; i < childs.length; i++) {

        let result = DoActionWithField(parent, childs[i], searchPath, action, path, i);
        childs[i] = result.field;

        if (result.field === undefined) {
            childs = [
                ...childs.slice(0, i),
                ...childs.slice(i + 1, childs.length)
            ];
            return {field: childs, isChanged: true}
        }

        if (result.isChanged) {
            return {field: childs, isChanged: result.isChanged}
        }
    }

    return {field: childs, isChanged: false}
};

const DoActionWithField = (parent, current, searchPath, action, path, index) => {

    const curPath =  current.name !== undefined ? path + "^" + current.name + index : path;

    if (curPath === searchPath) {
        return { field: action(parent, current, curPath), isChanged: true };
    }

    if (current.fields) {

        let result = DoActionWithFieldChilds(current, current.fields, searchPath, action, curPath);
        current.fields = result.field;
        if (result.isChanged) {
            return { field: current, isChanged: true }
        }
    }

    if (current.domainStructure && current.domainStructure.internalStructure &&
        current.domainStructure.internalStructure.fields &&
        current.domainStructure.internalStructure.fields.length > 0) {

        let result = DoActionWithFieldChilds(current, current.domainStructure.internalStructure.fields,
            searchPath, action, curPath);
        current.domainStructure.internalStructure.fields = result.field;
        if (result.isChanged) {
            return { field: current, isChanged: true }
        }
    }

    return { field: current, isChanged: false }
};

const DoActionWithDataSetsField = (data, path, updateEntity, action) => {

    const dataSets = [data.input, data.output, data.state, data.local];

    for(let i=0; i<dataSets.length; i++) {

        const alias = data.getDataSetAlias(dataSets[i]);

        let result = DoActionWithField(null, dataSets[i], path, action, alias, "");
        dataSets[i] = result.field;

        if (result.isChanged && updateEntity) {
            updateEntity({
                [alias]: dataSets[i]
            });
            return true;
        }
    }

    return false;
};

const GetTreeFieldByPath = (data, path) => {

    let curField = null;

    DoActionWithDataSetsField(data, path, null, (parent, field, curPath) => {
        curField = field;
        return field;
    });

    return curField;
};

/*
const CreateDataSetIfAbsent = (data, name) => {

    const dataSet = data[name];

    if (!dataSet) {

        data[name] = {
            fields: [],
            _type_: "dataset.Structure"
        }
    }

    return data;
};
*/

const RenderRulesTree = ({entity, data, updateEntity, openEditDialog, translate}) => {

    if (!data) {
        return null;
    }

    return (<PortsTree ports = {[
            data.input,
            data.output,
            data.state,
            data.local
        ]}
                       getAlias = {(dataSet) => data.getDataSetAlias(dataSet)}

                       isDisableSorting = {true}

                       renderGroupActions = {(path) => {

                           return (
                               <RenderGroupAction
                                   data={data}
                                   path={path}
                                   translate={translate}
                                   addAction={(parent, path, key) => {
                                       AddTreeElement(data, path, key, updateEntity);
                                   }}
                               />
                            );
                       }}

                       renderActions = {(path) => {
                           return (
                               <RenderTreeAction
                                    data={data}
                                    path={path}
                                    addAction={(parent, path, key) => {
                                        AddTreeElement(data, path, key, updateEntity);
                                    }}
                                    editAction={(path) => {
                                        openEditDialog(path);
                                    }}
                                    upAction={(path) => {
                                        MoveTreeElement(data, path, updateEntity, true);
                                    }}
                                    downAction={(path) => {
                                        MoveTreeElement(data, path, updateEntity, false);
                                    }}
                                    deleteAction={(path) => {
                                        DeleteTreeElement(translate, data, path, updateEntity);
                                    }}
                                    translate={translate}
                               />
                           );
                       }}

                       treeOnClick={(event) => {
                       }}
        />
    )
};

const RenderRuleAction = ({text, upAction, downAction, deleteAction}) => {

    const menu = (
        <Menu onClick={(e)=>{

            e.domEvent.stopPropagation();

            switch(e.key) {
                case("up"):
                    upAction(text);
                    break;
                case("down"):
                    downAction(text);
                    break;
                case("del"):
                    deleteAction(text);
                    break;
                default:
                    return;
            }
        }} mode="vertical">

            {upAction &&
            <Menu.Item key="up"><Avatar className="button-avatar" size="small"
                                        src='images/icon-core/arrow-up-modern.svg'/></Menu.Item>
            }

            {downAction &&
            <Menu.Item key="down"><Avatar className="button-avatar" size="small"
                                          src='images/icon-core/arrow-down-modern.svg'/></Menu.Item>
            }

            {deleteAction &&
            <Menu.Item key="del"><Avatar className="button-avatar" size="small"
                                         src='images/icon-core/delete-modern.svg' /></Menu.Item>
            }

        </Menu>
    );

    return <div className={"ruleAction"}><Dropdown overlay={menu} className="ruleAction">
        <Button>
            <Avatar src='images/icon-core/etl.svg' />
        </Button>
    </Dropdown></div>
};

const IsSearchStringContains = (value, filterState) => {

    if (!value || !filterState.filterStr) {
        return false;
    }

    const uppered = value.toUpperCase();

    let isFound = false;

    filterState.filterStr.split(" ").forEach((str) => {
        if (uppered.indexOf(str.toUpperCase()) >= 0) {
            isFound = true;
        }
    });

    return isFound;
};

const HighlightString = (elementId, value, filterState, filterApplied) => {

    let result = value;

    filterState.filterStr.split(" ").forEach((str) => {

        result = reactStringReplace(result, str, (match, i) => (
                <span key={i} style={{color: 'red'}}>{match}</span>
        ))
    });

    return <div className="rulesInlineEdit">
                {result}
                {elementId && filterApplied &&
                <Button onClick={() => {
                    filterState.edit(elementId);
                }}><Icon type="edit" className="alignedIcon"/></Button>
                }
            </div>
};

const WrapSearchableComponent = ({elementId, children, filterState, filterApplied, value}) => {

    if (value && filterState.filterStr
            && filterState.editableIds.indexOf(elementId) < 0
            && IsSearchStringContains(value, filterState)) {

        if (filterApplied) {
            filterApplied();
        }
        return HighlightString(elementId, value, filterState, filterApplied);
    }

    return children;
};


export { RenderCondition, RenderAction, RenderActionPanel, RenderRulesTree, EventsProcessorDataStructure,
    DoActionWithDataSetsField, GetTreeFieldByPath, RenderRuleAction, MoveRulesElement, DeleteRulesElement,
    WrapSearchableComponent, IsSearchStringContains }
