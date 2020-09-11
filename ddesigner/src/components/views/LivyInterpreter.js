import React, { Component } from 'react';
import { translate } from "react-i18next";
import resource from "../../Resource";
import { Select, Menu, Avatar, Tooltip } from 'antd'
import _ from 'lodash'
import AceEditor from 'react-ace';
import 'brace/mode/scala'
import 'brace/theme/tomorrow'
import SplitPane from 'react-split-pane';
import Pane from 'react-split-pane/lib/Pane';
import { cupOfCoffee } from '../../utils/consts';

const { Option } = Select

class LivyInterpreter extends Component {

    constructor(...args) {
        super(...args);
        this.state = {
            kind: "spark",
            history: []
        }
        this.splitterPosition = '50%'
    }

    readHistory() {
        let history = []
        const value = localStorage.getItem("livy_history")
        if (value) {
            history = JSON.parse(value)
        }
        return history
    }

    componentDidMount() {
        this.setState({ history: this.readHistory() })
    }

    componentDidUpdate() {
        let history = this.readHistory()
        if (!_.isEqual(history, this.state.history)) {
            this.setState({ history })
        }
    }

    saveHistory(kind, code) {
        const history = [{ kind, code }, ...this.state.history.filter(run => run.kind !== kind || run.code !== code)]
        if (history.length > 10) {
            history.length = 10
        }
        localStorage.setItem("livy_history", JSON.stringify(history))
        this.setState({ history })
    }

    render() {
        const { t } = this.props
        return (
            <div>
                <Menu onClick={e => {
                    if (e.key === "run") {
                        this.setState({ result: cupOfCoffee, image: undefined })
                        const params = { code: this.state.code, sessionId: this.state.sessionId, kind: this.state.kind }
                        this.saveHistory(params.kind, params.code)
                        resource.callByName(this.props.entity, "runCode", params).then(json => {
                            const text = _.get(json, ["output", "data", "text/plain"])
                            const image = _.get(json, ["output", "data", "image/png"])
                            this.setState({
                                result: text ? text : JSON.stringify(_.get(json, ["output"]), null, 4),
                                image,
                                sessionId: json.sessionId
                            })
                        }).catch(() => this.setState({ result: "" }))
                    }
                }} mode="horizontal">
                    <Menu.Item key={"run"}>
                        <Tooltip placement="left" title={t("run")}><Avatar
                            className="avatar-button-tool-panel"
                            src="images/icon-core/lightning.svg" />&nbsp;
                        </Tooltip>
                    </Menu.Item>
                    <Select
                        showSearch
                        style={{ width: 200, marginRight: 2, marginTop: 2 }}
                        optionFilterProp="children"
                        value={this.state.kind}
                        onChange={(value) => {
                            this.setState({ kind: value })
                        }}
                    >
                        <Option key={"spark"} value={"spark"}>spark</Option>
                        <Option key={"pyspark"} value={"pyspark"}>pyspark</Option>
                        <Option key={"sparkr"} value={"sparkr"}>sparkr</Option>
                        <Option key={"sql"} value={"sql"}>sql</Option>
                    </Select>
                    <Select
                        style={{ width: 600, marginRight: 2, marginTop: 2 }}
                        optionFilterProp="children"
                        value={undefined}
                        onChange={(value) => {
                            const { kind, code } = this.state.history[value]
                            this.setState({ kind, code })
                        }}
                    >
                        {this.state.history.map((run, index) => <Option key={index}
                            value={index}>{`${run.kind}: ${run.code}`}</Option>)}
                    </Select>
                </Menu>
                <div style={{ overflow: 'hidden', height: "calc(100vh - 222px)" }}>
                    <SplitPane
                        split="horizontal"
                        primary="first"
                        onChange={(values) => {
                            this.refs.aceEditor.editor.resize()
                            if (this.refs.console) {
                                this.refs.console.editor.resize()
                            }
                            this.splitterPosition = values[1]
                        }}
                    >
                        <Pane style={{ height: '100%', width: '100%', overflow: 'auto' }}>
                            <AceEditor
                                ref={"aceEditor"}
                                mode={"scala"}
                                width={""}
                                onChange={(code) => {
                                    this.setState({ code })
                                }}
                                name={"test"}
                                editorProps={{ $blockScrolling: true }}
                                value={this.state.code}
                                theme={"tomorrow"}
                                debounceChangePeriod={500}
                                height={"100%"}
                            />
                        </Pane>
                        <Pane
                            initialSize={this.splitterPosition}
                            minSize={'10%'}
                            style={{ height: '100%', width: '100%', overflow: 'auto', marginBottom: '10px' }}
                        >
                            <AceEditor
                                ref={"console"}
                                mode={'scala'}
                                width={''}
                                height={'100%'}
                                theme={'tomorrow'}
                                fontSize={15}
                                editorProps={{ $blockScrolling: Infinity }}
                                value={this.state.result}
                                showPrintMargin={false}
                                showGutter={false}
                                focus={false}
                                readOnly={true}
                                minLines={5}
                                highlightActiveLine={false}
                            />
                            {this.state.image && <div>
                                <img alt='plot' src={`data:image/png;base64, ${this.state.image}`} />
                            </div>}
                        </Pane>
                    </SplitPane>
                </div>
            </div>
        )
    }
}

export default translate()(LivyInterpreter);
