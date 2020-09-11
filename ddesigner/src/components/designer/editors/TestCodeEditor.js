import React, { Component, Fragment } from 'react';
import AceEditor from 'react-ace';
import 'brace/mode/scala';
import 'brace/theme/tomorrow';
import 'brace/ext/searchbox';
import { translate } from 'react-i18next'
import { Form, Tooltip, Avatar, Button, Divider } from 'antd';
import resource from "../../../Resource";
import { cupOfCoffee } from '../../../utils/consts';
import SplitPane from 'react-split-pane';
import Pane from 'react-split-pane/lib/Pane';

class TestCodeEditor extends Component {

	constructor(...args) {
		super(...args);
		this.aceEditor = React.createRef();
		this.state = {
			sessionId: null,
			fullCode: null,
			runResult: null
		}
		this.splitterPosition = '50%'
	}

	createEditor(value, name, onChangeFunction) {
		return <AceEditor
			ref={"aceEditor"}
			name={name}
			mode={'scala'}
			width={'100%'}
			height={'100%'}
			theme={'tomorrow'}
			fontSize={15}
			debounceChangePeriod={500}
			editorProps={{ $blockScrolling: Infinity }}
			onChange={newValue => onChangeFunction(newValue)}
			value={value}
			showPrintMargin={false}
		/>
	}

	createConsole(value) {
		return (
			<AceEditor
				ref={"console"}
				mode={'scala'}
				width={''}
				height={'100%'}
				theme={'tomorrow'}
				fontSize={15}
				minLines={5}
				editorProps={{ $blockScrolling: Infinity }}
				value={value}
				showPrintMargin={false}
				showGutter={false}
				focus={false}
				readOnly={true}
				highlightActiveLine={false}
			/>
		)
	}
	
	getReadableErrorView(queryError) {
		return queryError.status + '\n' + queryError.evalue + '\n' + queryError.traceback.join('')
	}

	runCode() {
		const {fullCode} = this.state
		this.setState({ runResult: cupOfCoffee, runError: null })
		resource.call({...this.props.entity, fileContent: fullCode}, "runPart", {}).then(json => {
			!json.result.valueCount ? (
				this.setState({ runResult: json.result["text/plain"] })
			) : (
					this.setState({ runResult: this.getReadableErrorView(json.result.values[0]) })
				)
		}).catch(() => this.setState({ runResult: null }))
	}

	showTransformationContent() {
		const { fullCode, runResult } = this.state
		const { t } = this.props
		return (
			<div style={{
				display: 'flex',
				height: 'calc(100vh - 154px)',
				flexDirection: 'column',
				overflow: 'hidden'
			}}>
				<Form layout={"inline"}>
					<Form.Item wrapperCol={{ span: 2, push: 14 }}>
						<Tooltip placement="top" title={t("savecode")}>
							<Button id="loadfullcode" shape="circle" style={{ border: 0 }} onClick={() => {
								this.loadCode()
							}}><Avatar className="avatar-button-tool-panel" src={"images/icon-core/upload-modern.svg"} />
							</Button>
						</Tooltip>
					</Form.Item>
					<Form.Item wrapperCol={{ span: 2, push: 14 }}>
						<Tooltip placement="top" title={t("run")}>
							<Button id="run" shape="circle" style={{ border: 0 }} onClick={() => {
								this.runCode()
							}}><Avatar className="avatar-button-tool-panel" src={"images/icon-core/arrow-right-modern.svg"} />
							</Button>
						</Tooltip>
					</Form.Item>
				</Form>
				<Divider style={{ marginTop: 0, marginBottom: 0 }} />
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
						{this.createEditor(fullCode ? fullCode : undefined, 'transformEditor', (newValue) => this.setState({ fullCode: newValue }))}
					</Pane>
					<Pane
						initialSize={this.splitterPosition}
						style={{ height: '100%', width: '100%', overflow: 'auto', marginBottom: '10px' }}
					>
						{runResult && this.createConsole(runResult)}
					</Pane>
				</SplitPane>
			</div>
		)
	}

	loadCode() {
		const entity = this.props.activeObject
		this.setState({ fullCode: null })
		resource.call({
			_type_: entity._type_,
			e_id: entity.e_id,
		}, 'generate', {}).then(json => json.result === true ? this.setState({ fullCode: json.fileContent }) : undefined).catch(
			() => this.setState({ fullCode: null })
		)
	}

	componentDidMount() {
		this.setState({
			fullCode: this.loadCode()
		})
	}
	
	render() {
		return (
			<Fragment>
				{this.showTransformationContent()}
			</Fragment>
		)
	}

}

export default translate()(TestCodeEditor);
