import React, { Suspense } from 'react'
import Logs from './Logs'
import Processes from './Processes'
import ObjectExplorer from './ObjectExplorer'
import ViewContainer from './views/ViewContainer'
import GridContainer from './views/GridContainer'
import DisplayList from './views/DisplayList'
import Validation from './bottom/Validation'
import Run from './bottom/Run'
import EmbeddedForm from './views/EmbeddedForm'
import FormView from './views/FormView'
import LivyView from './views/LivyView'
import SqlEditor from './designer/editors/SqlEditor'
import SparkEditor from './designer/editors/SparkEditor'
import SourceCodeEditor from './designer/editors/SourceCodeEditor'
import TestCodeEditor from './designer/editors/TestCodeEditor'
import GroupWithStateEditor from './designer/editors/GroupWithStateEditor'
import ProjectionEditor from './designer/editors/ProjectionEditor'
import JoinEditor from './designer/editors/ProjectionEditor'

import UnionEditor from './designer/editors/UnionEditor'
import LocalSourceEditor from './views/LocalView'
import LocalTargetEditor from './views/LocalView'
import CSVSourceEditor from './views/LocalView'
import CSVTargetEditor from './views/LocalView'
import TableSourceEditor from './views/TableView'
import TableTargetEditor from './views/TableView'
import ObjectInspector from './views/ObjectInspector'
import ExpressionEditor from './designer/editors/ExpressionEditor'
import XmlEditor from './designer/editors/XmlEditor'
import SelectionEditor from './designer/editors/SelectionEditor'
import AggregationEditor from './designer/editors/AggregationEditor'
import Search from './Search'
import AvroSourceEditor from './designer/editors/AvroSourceEditor'
import HiveSourceEditor from './views/HiveView'
import HiveTargetEditor from './views/TableView'
import ApplicationContainer from './views/ApplicationContainer'
import ApplicationInfo from './views/ApplicationInfo'
import EventsProcessorView  from './views/EventsProcessorView'
import VCSInfo from './views/VCSInfo'
import MLFlowView from './views/MLFlowView'
import { AppContext } from './../App.js';

const TransformationDesignerView = React.lazy(() => import('./views/TransformationDesignerView'));
const WorkflowDesignerView  = React.lazy(() => import('./views/WorkflowDesignerView'));

const HDFSView = React.lazy(() => import( './views/HDFSView'));
const OozieView = React.lazy(() => import( './views/OozieView'));
const LineAge = React.lazy(() => import( './bottom/LineAge'));
const JSONView = React.lazy(() => import( './views/JSONView'));
const ImportWizardView = React.lazy(() => import( './views/ImportWizardView'));
const AtlasView = React.lazy(() => import( './views/AtlasView'));
const AirflowView = React.lazy(() => import( './views/AirflowView'));
const StateMachineInstanceView = React.lazy(() => import( './views/StateMachineInstanceView'));

const Metadata = React.lazy(() => import('./dataspace/Metadata'));
const Constructor = React.lazy(() => import('./dataspace/Constructor'));
const QueryConstructor = React.lazy(() => import('./dataspace/QueryConstructor'));
const DatasetView = React.lazy(() => import('./dataspace/DatasetView'));
const WorkspaceView = React.lazy(() => import('./dataspace/WorkspaceView'));
const WorkspaceGridView = React.lazy(() => import('./dataspace/WorkspaceGridView'));
const NotebookView = React.lazy(() => import('./dataspace/notebook/NotebookView'));

const Components = {
    Logs, Processes, ObjectExplorer, TransformationDesignerView, WorkflowDesignerView, LineAge, ViewContainer, GridContainer,
    DisplayList, SqlEditor, EmbeddedForm, FormView, LivyView, HDFSView, OozieView, ProjectionEditor, SparkEditor, JoinEditor,
    UnionEditor, SourceCodeEditor, GroupWithStateEditor, LocalSourceEditor, LocalTargetEditor, TableSourceEditor, TableTargetEditor,
    ObjectInspector, ExpressionEditor, XmlEditor, SelectionEditor, AggregationEditor, AvroSourceEditor, Search, HiveSourceEditor, HiveTargetEditor,
    Validation, Run, ApplicationContainer, CSVSourceEditor, CSVTargetEditor, 
    Metadata, Constructor, DatasetView, 
    ApplicationInfo, WorkspaceView, WorkspaceGridView, QueryConstructor, NotebookView, 
    EventsProcessorView, JSONView, ImportWizardView, AtlasView, VCSInfo,
    StateMachineInstanceView, TestCodeEditor, AirflowView, MLFlowView
}

const createComponent = (name, props) => {
    const Component = Components[name]
    return Component && 
            <AppContext.Consumer>{context =><Suspense fallback={<h1>Loading…</h1>} ><Component {...props} context={context} /></Suspense>}</AppContext.Consumer>
}

export default createComponent
