import numpy as np
import pandas as pd
from sklearn.base import BaseEstimator
from sklearn.preprocessing import QuantileTransformer
from sklearn.utils.validation import check_is_fitted
from cuppa.logger import LoggerMixin


class SigCohortQuantileTransformer(BaseEstimator, LoggerMixin):
    def __init__(
        self,
        *,
        n_quantiles: int = 1000,
        #output_distribution="uniform",
        ignore_implicit_zeros: bool = False,
        subsample: int = 10_000,
        random_state: int = None,
        copy: bool = True,

        clip_upper: bool = True,
        include_feat_values: bool = True
    ):
        self.n_quantiles = n_quantiles
        #self.output_distribution = output_distribution
        self.ignore_implicit_zeros = ignore_implicit_zeros
        self.subsample = subsample
        self.random_state = random_state
        self.copy = copy

        self.clip_upper = clip_upper
        self.include_feat_values = include_feat_values

    def _check_X(self, X: pd.DataFrame) -> None:
        if np.any(X < 0):
            self.logger.error("`X must not contain negative values`")
            raise ValueError

        if not isinstance(X, pd.DataFrame):
            self.logger.error("`X` must be a pandas DataFrame")
            raise TypeError

    def fit(self, X: pd.DataFrame, y: pd.Series) -> "SigCohortQuantileTransformer":
        self._check_X(X)

        transformers = {}
        for class_i in np.unique(y):
            X_i = X[y==class_i]
            n_quantiles_i = min([self.n_quantiles, X_i.shape[0]])

            transformer = QuantileTransformer(
                n_quantiles = n_quantiles_i,
                output_distribution = "uniform",
                #output_distribution = self.output_distribution,
                ignore_implicit_zeros = self.ignore_implicit_zeros,
                subsample = self.subsample,
                random_state = self.random_state,
                copy = self.copy
            )

            transformer.fit(X_i, y)
            transformers[class_i] = transformer

        self.transformers_ = transformers
        self.feature_names_in_ = X.columns

        return self

    def transform(self, X: pd.DataFrame, clip_upper: bool = None, include_feat_values: bool = None) -> pd.DataFrame:

        ## Checks
        check_is_fitted(self, "transformers_")
        self._check_X(X)

        ## Args
        if clip_upper is None:
            clip_upper = self.clip_upper

        if include_feat_values is None:
            include_feat_values = self.include_feat_values

        ## Main
        X_trans = {}
        for class_i, transformer in self.transformers_.items():

            X_trans_i = transformer.transform(X)

            if not clip_upper:
                q_max = transformer.quantiles_[-1]
                X_trans_i = np.where(X>q_max, X/q_max, X_trans_i)

            X_trans_i = pd.DataFrame(X_trans_i, columns=self.feature_names_in_, index=X.index)
            X_trans[class_i] = X_trans_i

        X_trans = pd.concat(X_trans) ## shape: (n_classes, n_samples) x n_features

        ## Convert shape (n_samples, n_features) x n_classes
        X_trans = X_trans.unstack(level=1).transpose()

        ## Add feature values
        index_names = ["sample_id","feat_name"]
        if include_feat_values:
            X_trans["feat_value"] = X.unstack()
            index_names = index_names + ["feat_value"]

        ## Row order
        X_trans.index.names = ["feat_name", "sample_id"]
        X_trans = X_trans.reset_index().set_index(index_names)
        X_trans = X_trans.loc[X.index]

        return X_trans

    def fit_transform(self, X: pd.DataFrame, y: pd.Series) -> pd.DataFrame:
        self.fit(X,y)
        return self.transform(X)

    def set_output(self, transform: str = None) -> "SigCohortQuantileTransformer":
        return self
